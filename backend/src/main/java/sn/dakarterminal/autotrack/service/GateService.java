package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;
import sn.dakarterminal.autotrack.repository.*;
import sn.dakarterminal.autotrack.security.TicketSigner;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Portail de sortie. Le portique RFID (ou le scan du ticket) identifie le véhicule ;
 * l'application croise identité, ticket signé et autorisations.
 * La barrière ne s'ouvre jamais seule : l'agent valide après ses contrôles.
 */
@Service
@Transactional
public class GateService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm").withZone(ZoneId.of("Africa/Dakar"));

    private final VehicleRepository vehicles;
    private final TicketRepository tickets;
    private final FormalityRepository formalities;
    private final InspectionRepository inspections;
    private final GatePassageRepository passages;
    private final MovementRepository movements;
    private final ReadEventRepository reads;
    private final AlertRepository alerts;
    private final NotificationService notifications;
    private final TicketSigner signer;
    private final KeyEventRepository keys;

    public GateService(VehicleRepository vehicles, TicketRepository tickets, FormalityRepository formalities,
                       InspectionRepository inspections, GatePassageRepository passages, MovementRepository movements,
                       ReadEventRepository reads, AlertRepository alerts, NotificationService notifications,
                       TicketSigner signer, KeyEventRepository keys) {
        this.keys = keys;
        this.vehicles = vehicles;
        this.tickets = tickets;
        this.formalities = formalities;
        this.inspections = inspections;
        this.passages = passages;
        this.movements = movements;
        this.reads = reads;
        this.alerts = alerts;
        this.notifications = notifications;
        this.signer = signer;
    }

    public GateResult scan(GateScanRequest req, String reader) {
        Vehicle v = null;
        String mode;
        if (req.rfidEpc() != null && !req.rfidEpc().isBlank()) {
            v = vehicles.findByAnyCode(req.rfidEpc()).orElse(null);
            mode = req.ticketQr() != null ? "RFID+QR" : "RFID";
        } else if (req.vin() != null && !req.vin().isBlank()) {
            // VIN, QR « VH:… », clé « KEY:… », code DAT-…, EPC ou fin de VIN
            v = vehicles.findByAnyCode(req.vin()).orElse(null);
            mode = "RFID";
        } else {
            mode = "QR_TICKET";
        }
        Ticket ticket = null;
        if (req.ticketQr() != null && !req.ticketQr().isBlank()) {
            ticket = tickets.findByTicketNumber(TicketSigner.numberOf(req.ticketQr().trim()).toUpperCase(Locale.ROOT)).orElse(null);
            if (v == null && ticket != null) v = ticket.getVehicle();
        }
        if (v == null) throw ApiException.notFound("Véhicule (étiquette ou ticket non reconnu)");
        if (ticket == null) {
            ticket = tickets.findFirstByVehicleOrderByIssuedAtDesc(v).filter(t -> !t.isUsed()).orElse(null);
        }

        List<GateCheck> checks = new ArrayList<>();
        boolean blocking = false;
        Instant now = Instant.now();

        checks.add(new GateCheck("IDENTITE", "Étiquette lue", "VIN …" + v.getVin().substring(9)
                + (mode.startsWith("RFID") ? " · étiquette intacte" : " · via le ticket"), "OK"));

        String presented = req.ticketQr() == null ? "" : req.ticketQr().trim();
        boolean qrScanned = presented.startsWith("TKT.");
        boolean typedNumber = !presented.isEmpty() && !qrScanned;
        if (ticket == null) {
            checks.add(new GateCheck("TICKET", "Aucun ticket valide", "Aucun ticket émis (ou ticket déjà utilisé) pour ce véhicule", "KO"));
            blocking = true;
        } else {
            // QR scanné : on vérifie la signature du QR présenté ; sinon celle du ticket enregistré.
            TicketSigner.Verified ver = signer.verify(qrScanned ? presented : ticket.getQrToken(), v.getVin());
            if (!ver.signatureOk()) {
                checks.add(new GateCheck("TICKET", "Ticket non conforme", "Signature invalide ou ticket d'un autre véhicule", "KO"));
                blocking = true;
            } else if (ticket.isUsed()) {
                checks.add(new GateCheck("TICKET", "Ticket déjà utilisé", "Utilisé le " + DMY.format(ticket.getUsedAt()), "KO"));
                blocking = true;
            } else if (ticket.getValidUntil().isBefore(now)) {
                checks.add(new GateCheck("TICKET", "Ticket expiré", "Expiré le " + DMY.format(ticket.getValidUntil()), "KO"));
                blocking = true;
            } else if (qrScanned) {
                checks.add(new GateCheck("TICKET", "Ticket = véhicule", ticket.getTicketNumber() + " · signature valide · 1re utilisation", "OK"));
            } else if (typedNumber) {
                // Numéro tapé à la main (QR illisible) : l'agent contrôle le ticket du client.
                checks.add(new GateCheck("TICKET", "Ticket saisi à la main", "Contrôler le ticket " + ticket.getTicketNumber() + " présenté par le client", "MANUAL"));
            } else {
                // Lecture RFID / VIN seule : le client doit encore présenter son ticket.
                checks.add(new GateCheck("TICKET", "Ticket à présenter", "Ticket " + ticket.getTicketNumber() + " valide : le scanner ou le vérifier", "MANUAL"));
            }
        }

        Map<FormalityType, Formality> fs = new EnumMap<>(FormalityType.class);
        formalities.findByVehicleOrderByIdAsc(v).forEach(f -> fs.put(f.getType(), f));
        Formality bae = fs.get(FormalityType.BAE);
        if (bae != null && bae.isReceived()) {
            checks.add(new GateCheck("AUTORISATION", "Autorisation de sortie", "BAE reçu le " + DMY.format(bae.getReceivedAt()), "OK"));
        } else {
            checks.add(new GateCheck("AUTORISATION", "Autorisation de sortie", "BAE non reçu", "KO"));
            blocking = true;
        }
        Formality pay = fs.get(FormalityType.PAIEMENT);
        boolean paid = pay != null && pay.isReceived();
        checks.add(new GateCheck("PAIEMENT", "Frais de parc", paid ? "Payés" : "Non payés", paid ? "OK" : "KO"));
        blocking |= !paid;
        Formality others = fs.get(FormalityType.BON_LIVRAISON);
        if (others != null && !others.isReceived()) {
            checks.add(new GateCheck("BL_ARMATEUR", "Bon de livraison armateur", "Manquant", "KO"));
            blocking = true;
        }

        if (!blocking) {
            checks.add(new GateCheck("BENEFICIAIRE", "Bénéficiaire",
                    "À vérifier : " + VehicleService.mask(v.getBeneficiaryName())
                            + (v.getBeneficiaryIdSuffix() == null ? "" : " · CNI …" + v.getBeneficiaryIdSuffix()), "MANUAL"));
            int reserves = inspections.findByVehicle(v).map(i -> i.getReserves().size()).orElse(0);
            // Armoire à clés : la clé doit avoir été sortie pour ce véhicule
            String vinTail = v.getVin().substring(11);
            var lastKey = keys.findFirstByVinOrderByAtDesc(v.getVin());
            checks.add(new GateCheck("CLES", "Clés du véhicule", lastKey.map(k -> switch (k.getAction()) {
                case SORTIE -> "Clé sortie de l'armoire par " + k.getHolder() + " : vérifier qu'elle correspond (QR KEY:…" + vinTail + ")";
                case DEPOSEE -> "Clé encore dans l'armoire (" + (k.getSlot() == null ? "emplacement non renseigné" : k.getSlot()) + ") : la récupérer";
                case REMISE_CLIENT -> "Clé déjà remise au client";
            }).orElse("Aucun mouvement de clé enregistré : vérifier la clé remise"), "MANUAL"));
            checks.add(new GateCheck("ETAT", "État du véhicule",
                    reserves == 0 ? "À vérifier : aucune réserve à la réception" : "À vérifier : " + reserves + " réserve(s) connue(s)", "MANUAL"));
        } else if (v.getPlace() != null) {
            checks.add(new GateCheck("POSITION", "Dernière position connue", v.getPlace().label(), "INFO"));
        }

        GatePassage p = new GatePassage();
        p.setVin(v.getVin());
        p.setTicketNumber(ticket == null ? null : ticket.getTicketNumber());
        p.setLane(req.lane() == null ? 1 : req.lane());
        p.setReadMode(mode);
        p.setReadAt(now);
        p.setDecision(blocking ? GateDecision.BLOCKED : GateDecision.PENDING_AGENT);
        p.setReason(blocking ? "Contrôles automatiques non satisfaits" : null);
        passages.save(p);
        reads.save(new ReadEvent(null, "PORTAIL", v.getVin(), ReadSource.RFID_PORTIQUE, "Portique voie " + p.getLane(), now));

        if (blocking) {
            alerts.save(new Alert("SORTIE_BLOQUEE", v.getVin(), "Sortie bloquée au portail : " + v.getShortCode(), reader));
            notifications.sms("Superviseur de parc", "Sortie bloquée au portail : " + v.getShortCode() + " non autorisé.");
        }
        return new GateResult(p.getId(), v.getVin(), p.getTicketNumber(), p.getDecision().name(),
                blocking ? "Sortie bloquée : véhicule non autorisé" : "Sortie possible après contrôle de l'agent",
                blocking ? "Le véhicule est lu au portail sans autorisation de sortie" : "Identité, ticket et autorisation concordent",
                checks);
    }

    /** Décision de l'agent : EXIT_VALIDATED (ouvrir) ou BLOCKED (garder fermé). */
    public GatePassageDto decide(Long passageId, String decision, String reason, String agent) {
        GatePassage p = passages.findById(passageId).orElseThrow(() -> ApiException.notFound("Passage"));
        GateDecision d = GateDecision.valueOf(decision);
        if (d == GateDecision.EXIT_VALIDATED) {
            if (p.getDecision() != GateDecision.PENDING_AGENT) {
                throw ApiException.conflict("Ce passage ne peut pas être validé (contrôles automatiques non satisfaits)");
            }
            Vehicle v = vehicles.findByVin(p.getVin()).orElseThrow(() -> ApiException.notFound("Véhicule"));
            Ticket t = tickets.findByTicketNumber(p.getTicketNumber()).orElseThrow(() -> ApiException.notFound("Ticket"));
            t.setUsed(true);
            t.setUsedAt(Instant.now());
            Movement m = new Movement(v, MovementType.SORTIE, v.getPlace() == null ? null : v.getPlace().getCode(),
                    "Portail", agent, Instant.now());
            m.setArrivalScannedAt(Instant.now());
            movements.save(m);
            v.setExitedAt(Instant.now());
            keys.save(new sn.dakarterminal.autotrack.domain.KeyEvent(v.getVin(),
                    sn.dakarterminal.autotrack.domain.KeyEvent.Action.REMISE_CLIENT, "Client", null, agent));
            if (v.isTagReusable() && v.getRfidEpc() != null) {
                // Étiquette suspendue récupérée au portail : libérée pour le véhicule suivant
                notifications.sms("Parc", "Étiquette " + v.getRfidEpc() + " récupérée au portail, disponible");
                v.setRfidEpc(null);
            }
        }
        p.setDecision(d);
        p.setReason(reason);
        p.setDecidedBy(agent);
        p.setDecidedAt(Instant.now());
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<GatePassageDto> recent() {
        return passages.findTop20ByOrderByReadAtDesc().stream().map(GateService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> todayStats() {
        Instant since = Instant.now().minus(Duration.ofHours(24));
        return Map.of("exits", passages.countByDecisionAndReadAtAfter(GateDecision.EXIT_VALIDATED, since),
                "blocked", passages.countByDecisionAndReadAtAfter(GateDecision.BLOCKED, since));
    }

    private static GatePassageDto toDto(GatePassage p) {
        return new GatePassageDto(p.getId(), p.getReadAt(), p.getVin(), p.getTicketNumber(), p.getLane(),
                p.getDecision().name(), p.getReason(), p.getDecidedBy());
    }
}
