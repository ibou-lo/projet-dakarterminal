package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;
import sn.dakarterminal.autotrack.repository.*;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lecture de l'état du parc. Calcule, pour chaque véhicule, le statut qui relie
 * la présence physique (place, scans) à la disponibilité administrative (formalités).
 */
@Service
@Transactional(readOnly = true)
public class ParkQueryService {

    public static final List<String> ZONES = List.of("A", "B", "C", "D");
    private static final Map<String, String> ZONE_LABELS = Map.of(
            "A", "Arrivages récents", "B", "Zone pilote", "C", "Utilitaires & engins", "D", "Pré-sortie");

    private final VehicleRepository vehicles;
    private final FormalityRepository formalities;
    private final MovementRepository movements;
    private final TicketRepository tickets;
    private final GatePassageRepository gatePassages;
    private final InspectionRepository inspections;
    private final ReadEventRepository reads;
    private final ParkingPlaceRepository places;
    private final ParkZoneRepository zoneRepo;

    public ParkQueryService(VehicleRepository vehicles, FormalityRepository formalities, MovementRepository movements,
                            TicketRepository tickets, GatePassageRepository gatePassages,
                            InspectionRepository inspections, ReadEventRepository reads,
                            ParkingPlaceRepository places, ParkZoneRepository zoneRepo) {
        this.vehicles = vehicles;
        this.formalities = formalities;
        this.movements = movements;
        this.tickets = tickets;
        this.gatePassages = gatePassages;
        this.inspections = inspections;
        this.reads = reads;
        this.places = places;
        this.zoneRepo = zoneRepo;
    }

    /** Photographie de l'état du parc, chargée en quelques requêtes groupées. */
    public record Snapshot(List<Vehicle> vehicles,
                           Map<Long, List<Formality>> formalitiesByVehicle,
                           Set<String> openMoveVins,
                           Map<Long, Ticket> lastTicketByVehicle,
                           Set<String> atGateVins,
                           Map<Long, Integer> reservesByVehicle,
                           Map<String, ReadEvent> lastReadByVin) {

        public List<Formality> formalities(Vehicle v) {
            return formalitiesByVehicle.getOrDefault(v.getId(), List.of());
        }
    }

    public Snapshot snapshot() {
        List<Vehicle> all = vehicles.findAllWithPlace();
        Map<Long, List<Formality>> fByV = formalities.findAllWithVehicle().stream()
                .sorted(Comparator.comparingInt((Formality f) -> f.getType().ordinal()))
                .collect(Collectors.groupingBy(f -> f.getVehicle().getId(), LinkedHashMap::new, Collectors.toList()));
        Set<String> openMoves = movements.findMissingArrival(List.of(MovementType.DEPLACEMENT, MovementType.PRE_SORTIE))
                .stream().map(m -> m.getVehicle().getVin()).collect(Collectors.toSet());
        Map<Long, Ticket> lastTicket = new HashMap<>();
        for (Ticket t : tickets.findAll()) {
            Long vid = t.getVehicle().getId();
            Ticket prev = lastTicket.get(vid);
            if (prev == null || t.getIssuedAt().isAfter(prev.getIssuedAt())) {
                lastTicket.put(vid, t);
            }
        }
        Set<String> atGate = gatePassages.findAll().stream()
                .filter(g -> g.getDecision() == GateDecision.PENDING_AGENT)
                .map(GatePassage::getVin).collect(Collectors.toSet());
        Map<Long, Integer> reserves = new HashMap<>();
        for (Inspection i : inspections.findAll()) {
            reserves.put(i.getVehicle().getId(), i.getReserves().size());
        }
        Map<String, ReadEvent> lastRead = new HashMap<>();
        for (ReadEvent r : reads.findAll()) {
            if (r.getVin() == null) continue;
            ReadEvent prev = lastRead.get(r.getVin());
            if (prev == null || r.getReadAt().isAfter(prev.getReadAt())) {
                lastRead.put(r.getVin(), r);
            }
        }
        return new Snapshot(all, fByV, openMoves, lastTicket, atGate, reserves, lastRead);
    }

    // ------------------------------------------------------------------ statut

    public VehicleStatus status(Vehicle v, Snapshot s) {
        if (v.getExitedAt() != null) return VehicleStatus.EXITED;
        if (!v.isPositionConfirmed() || s.openMoveVins().contains(v.getVin())) return VehicleStatus.POSITION_TO_CONFIRM;
        return allReceived(s.formalities(v)) ? VehicleStatus.READY : VehicleStatus.AWAITING_AUTHORIZATION;
    }

    public PickupStage stage(Vehicle v, Snapshot s) {
        if (v.getExitedAt() != null) return PickupStage.SORTI;
        if (s.atGateVins().contains(v.getVin())) return PickupStage.AU_PORTAIL;
        Ticket t = s.lastTicketByVehicle().get(v.getId());
        if (t != null && !t.isUsed()) return PickupStage.TICKET_EMIS;
        if (allReceived(s.formalities(v))) return PickupStage.AUTORISE_TICKET_A_EMETTRE;
        return PickupStage.FORMALITES_EN_COURS;
    }

    public static boolean allReceived(List<Formality> fs) {
        return !fs.isEmpty() && fs.stream().allMatch(Formality::isReceived);
    }

    public VehicleSummary summary(Vehicle v, Snapshot s) {
        List<Formality> fs = s.formalities(v);
        int done = (int) fs.stream().filter(Formality::isReceived).count();
        VehicleStatus st = status(v, s);
        ParkingPlace p = v.getPlace();
        ReadEvent last = s.lastReadByVin().get(v.getVin());
        LastScan scan = last == null ? null : new LastScan(last.getReadAt(), last.getReader(), last.getSource().name());
        long days = v.getDisembarkedAt() == null ? 0
                : Duration.between(v.getDisembarkedAt(), v.getExitedAt() != null ? v.getExitedAt() : Instant.now()).toDays();
        return new VehicleSummary(v.getVin(), v.getShortCode(), v.getModel(), v.getColor(), v.getModelYear(),
                v.getEscale(), v.getBlNumber(),
                v.getExitedAt() != null ? "SORTI" : (p == null ? null : p.getCode()),
                v.getExitedAt() != null ? "Sorti" : (p == null ? "Non affecté" : p.label()),
                p == null ? null : p.getZone(),
                st.name(), st.getLabel(), stage(v, s).name(), done, fs.size(),
                s.reservesByVehicle().getOrDefault(v.getId(), 0), days, scan);
    }

    public List<VehicleSummary> list(String status, String zone, String q) {
        Snapshot s = snapshot();
        String query = q == null ? null : q.trim().toUpperCase(Locale.ROOT);
        return s.vehicles().stream()
                .map(v -> summary(v, s))
                .filter(x -> status == null || status.isBlank() || x.status().equals(status))
                .filter(x -> zone == null || zone.isBlank() || zone.equals(x.zone()))
                .filter(x -> query == null || query.isBlank()
                        || x.vin().contains(query)
                        || x.shortCode().contains(query)
                        || (x.blNumber() != null && x.blNumber().toUpperCase(Locale.ROOT).contains(query))
                        || (x.placeCode() != null && x.placeCode().replace("-", "").contains(query.replace("-", "").replace("·", "").replace(" ", ""))))
                .sorted(Comparator.comparing(VehicleSummary::status).thenComparing(VehicleSummary::vin))
                .toList();
    }

    public Map<String, Long> countByStatus() {
        Snapshot s = snapshot();
        Map<String, Long> m = new LinkedHashMap<>();
        for (VehicleStatus st : VehicleStatus.values()) m.put(st.name(), 0L);
        s.vehicles().forEach(v -> m.merge(status(v, s).name(), 1L, Long::sum));
        m.put("ALL", s.vehicles().stream().filter(v -> v.getExitedAt() == null).count());
        return m;
    }

    // ------------------------------------------------------------------ carte

    /** « FRESH_1H », « H1_6 », « H6_24 », « OLD » ou « NEVER ». */
    public static String freshness(Instant lastReadAt) {
        if (lastReadAt == null) return "NEVER";
        long minutes = Duration.between(lastReadAt, Instant.now()).toMinutes();
        if (minutes < 60) return "FRESH_1H";
        if (minutes < 360) return "H1_6";
        if (minutes < 1440) return "H6_24";
        return "OLD";
    }

    public ParkOverview overview(String sourceFilter) {
        Snapshot s = snapshot();
        Map<String, Vehicle> byPlace = new HashMap<>();
        for (Vehicle v : s.vehicles()) {
            if (v.getExitedAt() == null && v.getPlace() != null) byPlace.put(v.getPlace().getCode(), v);
        }
        Set<String> incoherentPlaces = incoherences().stream()
                .map(Incoherence::placeCode).filter(Objects::nonNull).collect(Collectors.toSet());
        List<ZoneMap> zones = new ArrayList<>();
        Map<String, String> zoneLabels = new HashMap<>(ZONE_LABELS);
        zoneRepo.findAll().forEach(z -> zoneLabels.put(z.getCode(), z.getLabel()));
        Map<String, List<ParkingPlace>> placesByZone = places.findAllByOrderByZoneAscAlleeAscNumeroAsc().stream()
                .collect(Collectors.groupingBy(ParkingPlace::getZone, TreeMap::new, Collectors.toList()));
        for (var e : placesByZone.entrySet()) {
            int occupied = 0, ready = 0, fresh = 0;
            Map<Integer, List<PlaceCell>> byAllee = new TreeMap<>();
            for (ParkingPlace p : e.getValue()) {
                Vehicle v = byPlace.get(p.getCode());
                String state = "FREE";
                if (v != null) {
                    occupied++;
                    VehicleStatus st = status(v, s);
                    state = st == VehicleStatus.READY ? "READY" : "OCCUPIED";
                    if (st == VehicleStatus.READY) ready++;
                }
                boolean sourceMatch = sourceFilter == null || sourceFilter.isBlank() || sourceFilter.equals("ALL")
                        || (p.getLastReadSource() != null && p.getLastReadSource().name().equals(sourceFilter));
                String fr = sourceMatch ? freshness(p.getLastReadAt()) : "OLD";
                if (v != null && (fr.equals("FRESH_1H") || fr.equals("H1_6"))) fresh++;
                byAllee.computeIfAbsent(p.getAllee(), k -> new ArrayList<>()).add(new PlaceCell(
                        p.getCode(), p.getAllee(), p.getNumero(), state,
                        v == null ? null : v.getVin(), v == null ? null : v.getShortCode(),
                        p.getLastReadAt(), p.getLastReadSource() == null ? null : p.getLastReadSource().name(),
                        fr, incoherentPlaces.contains(p.getCode())));
            }
            zones.add(new ZoneMap(e.getKey(), zoneLabels.getOrDefault(e.getKey(), ""), e.getValue().size(),
                    occupied, ready, occupied == 0 ? 0 : Math.round(fresh * 100f / occupied),
                    new ArrayList<>(byAllee.values())));
        }
        long onPark = s.vehicles().stream().filter(v -> v.getExitedAt() == null).count();
        long confirmed = s.vehicles().stream().filter(v -> v.getExitedAt() == null && v.isPositionConfirmed()
                && !s.openMoveVins().contains(v.getVin())).count();
        long ready = s.vehicles().stream().filter(v -> status(v, s) == VehicleStatus.READY).count();
        long moves24 = movements.findSince(Instant.now().minus(Duration.ofHours(24))).size();
        return new ParkOverview(onPark, onPark == 0 ? 0 : Math.round(confirmed * 100f / onPark), ready, moves24, zones);
    }

    /**
     * Incohérences entre les lectures physiques et les fiches :
     * véhicule lu par un portique d'une autre zone, place occupée selon la fiche mais lue vide.
     */
    public List<Incoherence> incoherences() {
        List<Incoherence> out = new ArrayList<>();
        Instant since = Instant.now().minus(Duration.ofHours(24));
        List<ReadEvent> recent = reads.findByReadAtAfterOrderByReadAtDesc(since);
        Map<String, Vehicle> byVin = vehicles.findAllOnPark().stream()
                .collect(Collectors.toMap(Vehicle::getVin, v -> v));
        Set<String> seen = new HashSet<>();
        for (ReadEvent r : recent) {
            if (r.getSource() == ReadSource.RFID_PORTIQUE && r.getVin() != null && r.getPlaceCode() == null
                    && !"PORTAIL".equals(r.getZone())) {
                Vehicle v = byVin.get(r.getVin());
                if (v != null && v.getPlace() != null && !v.getPlace().getZone().equals(r.getZone())
                        && seen.add("Z" + v.getVin())) {
                    out.add(new Incoherence("ZONE", v.getVin(), v.getPlace().getCode(),
                            "Lu par le portique de la zone " + r.getZone() + " mais sa fiche indique " + v.getPlace().label(),
                            r.getReadAt()));
                }
            }
            if (r.getSource() == ReadSource.RAFALE && r.getVin() == null && r.getPlaceCode() != null
                    && seen.add("P" + r.getPlaceCode())) {
                vehicles.findOnPlace(r.getPlaceCode()).ifPresent(v -> out.add(new Incoherence("VIDE", v.getVin(),
                        r.getPlaceCode(), "Occupée selon la fiche, mais vide lors de l'inventaire en rafale",
                        r.getReadAt())));
            }
        }
        return out;
    }
}
