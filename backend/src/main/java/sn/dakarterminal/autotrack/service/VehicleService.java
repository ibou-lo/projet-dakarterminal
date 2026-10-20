package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;
import sn.dakarterminal.autotrack.repository.*;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.time.Instant;
import java.util.*;

/**
 * Cycle de vie physique du véhicule : réception, état, stationnement,
 * déplacements. Chaque étape est tracée (heure + agent).
 */
@Service
@Transactional
public class VehicleService {

    private final VehicleRepository vehicles;
    private final ParkingPlaceRepository places;
    private final InspectionRepository inspections;
    private final FormalityRepository formalities;
    private final MovementRepository movements;
    private final ReadEventRepository reads;
    private final TicketRepository tickets;
    private final GatePassageRepository gatePassages;
    private final AlertRepository alerts;
    private final ParkQueryService query;
    private final VerificationTaskRepository tasks;

    public VehicleService(VehicleRepository vehicles, ParkingPlaceRepository places, InspectionRepository inspections,
                          FormalityRepository formalities, MovementRepository movements, ReadEventRepository reads,
                          TicketRepository tickets, GatePassageRepository gatePassages, AlertRepository alerts,
                          ParkQueryService query, VerificationTaskRepository tasks) {
        this.vehicles = vehicles;
        this.places = places;
        this.inspections = inspections;
        this.formalities = formalities;
        this.movements = movements;
        this.reads = reads;
        this.tickets = tickets;
        this.gatePassages = gatePassages;
        this.alerts = alerts;
        this.query = query;
        this.tasks = tasks;
    }

    public Vehicle require(String vinOrCode) {
        return vehicles.findByAnyCode(vinOrCode).orElseThrow(() -> {
            String k = VehicleRepository.cleanCode(vinOrCode);
            if (k.startsWith("PL:") || k.matches("[A-Z]{1,3}-\\d{1,2}-\\d{1,3}")) {
                return ApiException.badRequest("Ce QR est celui d'une place (" + k + ") : scannez l'étiquette du véhicule ou sa clé");
            }
            if (k.startsWith("TKT.")) {
                return ApiException.badRequest("Ce QR est un ticket de retrait : scannez l'étiquette du véhicule");
            }
            return ApiException.notFound("Véhicule " + vinOrCode);
        });
    }

    // ------------------------------------------------------------ Réception

    /** Étape 1 : création du passeport numérique après lecture et confirmation du VIN. */
    public Passport receive(ReceptionRequest r, String agent) {
        String vin = VinService.normalize(r.vin());
        if (vehicles.findByVin(vin).isPresent()) {
            throw ApiException.conflict("Ce VIN a déjà un passeport numérique");
        }
        Vehicle v = new Vehicle(vin, r.model(), r.color(), r.modelYear(), r.escale(), r.blNumber());
        v.setBeneficiaryName(r.beneficiaryName());
        v.setBeneficiaryIdSuffix(r.beneficiaryIdSuffix());
        v.setForwarder(r.forwarder());
        v.setDisembarkedAt(Instant.now());
        v.setRfidEpc("E280-" + vin.substring(11));
        vehicles.save(v);
        for (FormalityType t : FormalityType.values()) {
            formalities.save(new Formality(v, t));
        }
        Movement m = new Movement(v, MovementType.DEBARQUEMENT, "Quai", null, agent, Instant.now());
        m.setArrivalScannedAt(Instant.now());
        m.setNote("VIN lu par photo et confirmé");
        movements.save(m);
        return passport(vin);
    }

    /** Étape 2 : état à la réception (photos, réserves, carburant, clés). */
    public Passport inspect(String vinOrCode, InspectionRequest r, String agent) {
        Vehicle v = require(vinOrCode);
        Inspection i = inspections.findByVehicle(v).orElseGet(() -> {
            Inspection n = new Inspection();
            n.setVehicle(v);
            return n;
        });
        i.setFuelLevel(r.fuelLevel());
        i.setKeysCount(r.keysCount());
        i.setMileage(r.mileage());
        i.getPhotos().clear();
        if (r.photos() != null) r.photos().forEach(p -> i.getPhotos().add(new Inspection.Photo(p.view(), p.storageRef())));
        i.getReserves().clear();
        if (r.reserves() != null) r.reserves().forEach(x ->
                i.getReserves().add(new Inspection.Reserve(x.location(), x.description(), x.photoView())));
        i.setInspectedBy(agent);
        i.setInspectedAt(Instant.now());
        inspections.save(i);
        return passport(v.getVin());
    }

    // ------------------------------------------------------------ Stationnement / déplacements

    public ParkingPlace resolvePlace(String scan) {
        String s = scan == null ? "" : scan.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("VH:") || s.startsWith("KEY:") || s.startsWith("TKT.")) {
            throw ApiException.badRequest("Ce QR n'est pas celui d'une place : scannez le QR « PL:… » fixé sur la place");
        }
        String code = (s.startsWith("PL:") ? s.substring(3) : s)
                .replace(" ", "").replace("·", "-").replace("_", "-").replace("/", "-");
        return places.findByCode(code).orElseThrow(() -> ApiException.notFound("Place " + scan));
    }

    /**
     * Étape 3 : affectation à une place, confirmée par le scan du QR de la place.
     * Si le véhicule était déjà placé, le mouvement est tracé comme un déplacement.
     */
    public Passport park(String vinOrCode, String placeScan, String agent) {
        Vehicle v = require(vinOrCode);
        ParkingPlace target = resolvePlace(placeScan);
        vehicles.findOnPlace(target.getCode()).ifPresent(other -> {
            if (!other.getId().equals(v.getId())) {
                throw ApiException.conflict("La place " + target.label() + " est déjà occupée par " + other.getShortCode());
            }
        });
        Instant now = Instant.now();
        // Clôture d'un déplacement démarré sans scan d'arrivée
        Optional<Movement> open = movements.findByVehicleOrderByStartedAtAsc(v).stream()
                .filter(m -> m.getArrivalScannedAt() == null).reduce((a, b) -> b);
        if (open.isPresent()) {
            Movement m = open.get();
            m.setToPlace(target.getCode());
            m.setArrivalScannedAt(now);
        } else {
            MovementType type = v.getPlace() == null ? MovementType.STATIONNEMENT
                    : ("D".equals(target.getZone()) ? MovementType.PRE_SORTIE : MovementType.DEPLACEMENT);
            Movement m = new Movement(v, type, v.getPlace() == null ? "Quai" : v.getPlace().getCode(),
                    target.getCode(), agent, now);
            m.setArrivalScannedAt(now);
            m.setNote("Scan QR place");
            movements.save(m);
        }
        v.setPlace(target);
        v.setPositionConfirmed(true);
        target.setLastReadAt(now);
        target.setLastReadSource(ReadSource.QR);
        reads.save(new ReadEvent(target.getCode(), target.getZone(), v.getVin(), ReadSource.QR, agent, now));
        return passport(v.getVin());
    }

    /**
     * Affectation depuis le poste de supervision (web).
     * - confirmed = true : le superviseur a vérifié la position sur place → position confirmée (lecture MANUEL) ;
     * - confirmed = false : place réservée, position « à confirmer » et tâche envoyée à un agent,
     *   qui confirmera en scannant le QR de la place (le mouvement est alors clôturé automatiquement).
     */
    public Passport assign(String vinOrCode, String placeCode, boolean confirmed, String actor) {
        Vehicle v = require(vinOrCode);
        if (v.getExitedAt() != null) throw ApiException.conflict("Véhicule déjà sorti du terminal");
        ParkingPlace target = resolvePlace(placeCode);
        if (v.getPlace() != null && v.getPlace().getId().equals(target.getId()) && v.isPositionConfirmed()) {
            throw ApiException.conflict("Le véhicule est déjà en " + target.label());
        }
        vehicles.findOnPlace(target.getCode()).ifPresent(other -> {
            if (!other.getId().equals(v.getId())) {
                throw ApiException.conflict("La place " + target.label() + " est occupée par " + other.getShortCode());
            }
        });
        Instant now = Instant.now();
        // Un déplacement resté ouvert est remplacé par cette affectation
        movements.findByVehicleOrderByStartedAtAsc(v).stream()
                .filter(m -> m.getArrivalScannedAt() == null)
                .forEach(m -> {
                    m.setArrivalScannedAt(now);
                    m.setNote("Clôturé : réaffecté depuis le poste de supervision");
                });
        MovementType type = v.getPlace() == null ? MovementType.STATIONNEMENT
                : ("D".equals(target.getZone()) ? MovementType.PRE_SORTIE : MovementType.DEPLACEMENT);
        Movement m = new Movement(v, type, v.getPlace() == null ? "Quai" : v.getPlace().getCode(), target.getCode(), actor, now);
        v.setPlace(target);
        if (confirmed) {
            m.setArrivalScannedAt(now);
            m.setNote("Affectation web · position vérifiée par " + actor);
            v.setPositionConfirmed(true);
            target.setLastReadAt(now);
            target.setLastReadSource(ReadSource.MANUEL);
            reads.save(new ReadEvent(target.getCode(), target.getZone(), v.getVin(), ReadSource.MANUEL, actor, now));
        } else {
            m.setNote("Affectation web · scan du QR de la place attendu");
            v.setPositionConfirmed(false);
            tasks.save(new VerificationTask(v.getVin(), target.getCode(),
                    "Placer " + v.getShortCode() + " en " + target.label() + " et scanner le QR de la place",
                    "Agent zone " + target.getZone(), actor));
        }
        movements.save(m);
        return passport(v.getVin());
    }

    /** Démarre un déplacement (la position devient « à confirmer » jusqu'au scan d'arrivée). */
    public Passport startMove(String vinOrCode, String agent) {
        Vehicle v = require(vinOrCode);
        boolean alreadyOpen = movements.findByVehicleOrderByStartedAtAsc(v).stream().anyMatch(x -> x.getArrivalScannedAt() == null);
        if (alreadyOpen) return passport(v.getVin());   // déplacement déjà démarré (nouvel essai de l'agent)
        Movement m = new Movement(v, MovementType.DEPLACEMENT, v.getPlace() == null ? null : v.getPlace().getCode(),
                null, agent, Instant.now());
        m.setNote("Déplacement démarré");
        movements.save(m);
        return passport(v.getVin());
    }

    // ------------------------------------------------------------ Passeport

    @Transactional(readOnly = true)
    public Passport passport(String vinOrCode) {
        Vehicle v = require(vinOrCode);
        ParkQueryService.Snapshot s = query.snapshot();
        Vehicle snapV = s.vehicles().stream().filter(x -> x.getId().equals(v.getId())).findFirst().orElse(v);
        VehicleSummary sum = query.summary(snapV, s);

        List<FormalityDto> fs = formalities.findByVehicleOrderByIdAsc(v).stream()
                .map(f -> new FormalityDto(f.getType().name(), f.getType().getLabel(), f.getStatus().name(),
                        f.getReceivedAt(), f.getReference(), f.getSource()))
                .toList();

        InspectionDto insp = inspections.findByVehicle(v).map(i -> new InspectionDto(i.getFuelLevel(), i.getKeysCount(),
                i.getMileage(),
                i.getPhotos().stream().map(p -> new PhotoDto(p.getView(), p.getStorageRef())).toList(),
                i.getReserves().stream().map(x -> new ReserveDto(x.getLocation(), x.getDescription(), x.getPhotoView())).toList(),
                i.getInspectedBy(), i.getInspectedAt())).orElse(null);

        List<HistoryItem> history = new ArrayList<>();
        for (Movement m : movements.findByVehicleOrderByStartedAtAsc(v)) {
            String what = switch (m.getType()) {
                case DEBARQUEMENT -> "Débarquement · " + (m.getNote() == null ? "VIN enregistré" : m.getNote());
                case STATIONNEMENT -> "Stationné en " + label(m.getToPlace());
                case DEPLACEMENT -> m.isTraceComplete()
                        ? "Déplacé de " + label(m.getFromPlace()) + " vers " + label(m.getToPlace())
                        : "Déplacement depuis " + label(m.getFromPlace()) + " · arrivée non scannée";
                case PRE_SORTIE -> "Pré-sortie vers " + label(m.getToPlace());
                case SORTIE -> "Sortie du terminal";
            };
            history.add(new HistoryItem(m.getStartedAt(), m.getType().name(), what, m.getAgent()));
        }
        if (insp != null) {
            history.add(new HistoryItem(insp.inspectedAt(), "ETAT",
                    "État enregistré · " + insp.photos().size() + " photos, " + insp.reserves().size() + " réserve(s)",
                    insp.inspectedBy()));
        }
        formalities.findByVehicleOrderByIdAsc(v).stream().filter(f -> f.isReceived() && f.getType() == FormalityType.BAE)
                .forEach(f -> history.add(new HistoryItem(f.getReceivedAt(), "AUTORISATION",
                        "Autorisation de sortie reçue", f.getSource())));
        Ticket t = tickets.findFirstByVehicleOrderByIssuedAtDesc(v).orElse(null);
        if (t != null) {
            history.add(new HistoryItem(t.getIssuedAt(), "TICKET", "Ticket " + t.getTicketNumber() + " émis", t.getIssuedBy()));
        }
        gatePassages.findAll().stream().filter(g -> v.getVin().equals(g.getVin())).forEach(g ->
                history.add(new HistoryItem(g.getReadAt(), "PORTAIL", "Portail : " + switch (g.getDecision()) {
                    case PENDING_AGENT -> "lecture, contrôle agent en cours";
                    case EXIT_VALIDATED -> "sortie validée";
                    case BLOCKED -> "sortie bloquée";
                }, g.getDecidedBy() == null ? "Portique" : g.getDecidedBy())));
        // Chaîne de garde : lectures physiques (QR, portiques RFID, rafale, vérification manuelle)
        for (ReadEvent r : reads.findByVinOrderByReadAtAsc(v.getVin())) {
            String where = r.getPlaceCode() != null ? label(r.getPlaceCode()) : "zone " + r.getZone();
            String what = switch (r.getSource()) {
                case QR -> "Scan QR de la place " + where;
                case RFID_PORTIQUE -> "Lecture RFID · portique " + ("PORTAIL".equals(r.getZone()) ? "de sortie" : where);
                case RAFALE -> "Inventaire en rafale · " + where;
                case MANUEL -> "Position vérifiée manuellement · " + where;
            };
            history.add(new HistoryItem(r.getReadAt(), "LECTURE", what, r.getReader()));
        }
        history.removeIf(h -> h.at() == null);
        history.sort(Comparator.comparing(HistoryItem::at));

        List<String> activeAlerts = alerts.findByVinAndActiveTrue(v.getVin()).stream().map(Alert::getMessage).toList();
        String masked = mask(v.getBeneficiaryName());
        return new Passport(sum, masked + (v.getBeneficiaryIdSuffix() == null ? "" : " (CNI …" + v.getBeneficiaryIdSuffix() + ")"),
                v.getForwarder(), v.getRfidEpc(), fs, insp, history, t == null ? null : toDto(t, v), activeAlerts);
    }

    public static String label(String placeCode) {
        if (placeCode == null) return "?";
        return placeCode.contains("-") ? placeCode.replace("-", " · ") : placeCode;
    }

    public static String mask(String name) {
        if (name == null || name.isBlank()) return "—";
        String[] parts = name.trim().split("\\s+");
        String last = parts[parts.length - 1];
        String first = parts.length > 1 ? parts[0] + " " : "";
        return first + last.charAt(0) + "***";
    }

    public static TicketDto toDto(Ticket t, Vehicle v) {
        return new TicketDto(t.getTicketNumber(), v.getVin(), v.getShortCode(), v.getModel(),
                v.getPlace() == null ? null : v.getPlace().label(),
                mask(v.getBeneficiaryName()) + (v.getBeneficiaryIdSuffix() == null ? "" : " (CNI …" + v.getBeneficiaryIdSuffix() + ")"),
                t.getIssuedAt(), t.getValidUntil(), t.getSlot(), t.isUsed(), t.getQrToken());
    }
}
