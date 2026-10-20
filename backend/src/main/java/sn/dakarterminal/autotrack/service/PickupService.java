package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.config.AutoTrackProperties;
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
 * Préparation de l'enlèvement : formalités, alertes, tickets de retrait et
 * tableau des enlèvements.
 */
@Service
@Transactional
public class PickupService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Africa/Dakar"));
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Africa/Dakar"));

    private final VehicleService vehicleService;
    private final ParkQueryService query;
    private final FormalityRepository formalities;
    private final TicketRepository tickets;
    private final AlertRepository alerts;
    private final NotificationService notifications;
    private final TicketSigner signer;
    private final AutoTrackProperties props;

    public PickupService(VehicleService vehicleService, ParkQueryService query, FormalityRepository formalities,
                         TicketRepository tickets, AlertRepository alerts, NotificationService notifications,
                         TicketSigner signer, AutoTrackProperties props) {
        this.vehicleService = vehicleService;
        this.query = query;
        this.formalities = formalities;
        this.tickets = tickets;
        this.alerts = alerts;
        this.notifications = notifications;
        this.signer = signer;
        this.props = props;
    }

    /**
     * Enregistre la réception d'une formalité (ex. BAE reçu de la plateforme de formalités).
     * Déclenche les alertes validées et prévient le client s'il l'a demandé.
     */
    public Passport receiveFormality(String vinOrCode, FormalityType type, FormalityUpdate u, String actor) {
        Vehicle v = vehicleService.require(vinOrCode);
        Formality f = formalities.findByVehicleAndType(v, type)
                .orElseGet(() -> formalities.save(new Formality(v, type)));
        f.setStatus(FormalityStatus.RECEIVED);
        f.setReceivedAt(Instant.now());
        f.setReference(u == null ? null : u.reference());
        f.setSource(u == null || u.source() == null ? "Plateforme de formalités" : u.source());

        boolean allDone = formalities.findByVehicleOrderByIdAsc(v).stream().allMatch(Formality::isReceived);
        if (type == FormalityType.BAE) {
            for (Alert a : alerts.findByVinAndTypeAndActiveTrueAndTriggeredFalse(v.getVin(), "BAE_RECU")) {
                a.setTriggered(true);
                a.setTriggeredAt(Instant.now());
                notifications.sms("Transitaire " + Objects.requireNonNullElse(v.getForwarder(), ""),
                        "BAE reçu pour " + v.getShortCode() + " (" + v.getModel() + ").");
                if (a.isAutoTicket() && allDone) {
                    issueTicket(v.getVin(), null, "Assistant (alerte validée par " + a.getCreatedBy() + ")");
                }
                a.setActive(false);
            }
        }
        if (allDone && v.isClientNotify()) {
            notifications.sms("Client BL " + v.getBlNumber(),
                    "Bonne nouvelle : votre véhicule " + v.getShortCode() + " est prêt à enlever.");
        }
        return vehicleService.passport(v.getVin());
    }

    /** Remet une formalité en attente (utile pour rejouer la démonstration). */
    public Passport resetFormality(String vinOrCode, FormalityType type) {
        Vehicle v = vehicleService.require(vinOrCode);
        formalities.findByVehicleAndType(v, type).ifPresent(f -> {
            f.setStatus(FormalityStatus.PENDING);
            f.setReceivedAt(null);
            f.setReference(null);
        });
        tickets.findFirstByVehicleOrderByIssuedAtDesc(v).filter(t -> !t.isUsed()).ifPresent(tickets::delete);
        return vehicleService.passport(v.getVin());
    }

    /** Émet le ticket de retrait : seulement si le véhicule est localisé ET autorisé. */
    public TicketDto issueTicket(String vinOrCode, String slot, String actor) {
        Vehicle v = vehicleService.require(vinOrCode);
        List<Formality> fs = formalities.findByVehicleOrderByIdAsc(v);
        if (!ParkQueryService.allReceived(fs)) {
            throw ApiException.conflict("Formalités incomplètes : le véhicule n'est pas encore autorisé à sortir");
        }
        if (v.getPlace() == null || !v.isPositionConfirmed()) {
            throw ApiException.conflict("Position non confirmée : scannez la place avant d'émettre le ticket");
        }
        Optional<Ticket> existing = tickets.findFirstByVehicleOrderByIssuedAtDesc(v).filter(t -> !t.isUsed()
                && t.getValidUntil().isAfter(Instant.now()));
        if (existing.isPresent()) return VehicleService.toDto(existing.get(), v);

        Ticket t = new Ticket();
        t.setVehicle(v);
        t.setTicketNumber(String.format("TR-%d-%06d", java.time.Year.now().getValue(),
                Math.floorMod(Objects.hash(v.getVin(), Instant.now().toEpochMilli()), 1_000_000)));
        t.setIssuedAt(Instant.now());
        t.setValidUntil(Instant.now().plus(Duration.ofHours(props.ticket().validityHours())));
        t.setSlot(slot);
        t.setIssuedBy(actor);
        t.setQrToken(signer.sign(t.getTicketNumber(), v.getVin(), t.getValidUntil()));
        tickets.save(t);
        notifications.sms("Client BL " + v.getBlNumber(), "Votre ticket de retrait " + t.getTicketNumber()
                + " est disponible. Valable jusqu'au " + DMY.format(t.getValidUntil()) + ".");
        return VehicleService.toDto(t, v);
    }

    public void remindForwarder(String vinOrCode, String actor) {
        Vehicle v = vehicleService.require(vinOrCode);
        List<String> missing = formalities.findByVehicleOrderByIdAsc(v).stream().filter(f -> !f.isReceived())
                .map(f -> f.getType().getLabel()).toList();
        notifications.sms("Transitaire " + Objects.requireNonNullElse(v.getForwarder(), ""),
                "Relance " + v.getShortCode() + " : pièces manquantes " + missing + " (" + actor + ").");
    }

    /** Crée l'alerte « BAE reçu → prévenir le transitaire (+ ticket auto) ». */
    public Alert createBaeAlert(String vin, boolean autoTicket, String actor) {
        Alert a = new Alert("BAE_RECU", vin, "Alerte « BAE reçu » : prévenir le transitaire"
                + (autoTicket ? " et générer le ticket" : ""), actor);
        a.setAutoTicket(autoTicket);
        return alerts.save(a);
    }

    // ------------------------------------------------------------ Consultation des tickets

    /** Tickets émis, du plus récent au plus ancien (filtre : ACTIVE, USED, EXPIRED ou tous). */
    @Transactional(readOnly = true)
    public List<TicketDto> listTickets(String state) {
        Instant now = Instant.now();
        return tickets.findAllWithVehicle().stream()
                .filter(t -> state == null || state.isBlank()
                        || ("USED".equals(state) && t.isUsed())
                        || ("EXPIRED".equals(state) && !t.isUsed() && t.getValidUntil().isBefore(now))
                        || ("ACTIVE".equals(state) && !t.isUsed() && t.getValidUntil().isAfter(now)))
                .map(t -> VehicleService.toDto(t, t.getVehicle()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketDto ticket(String number) {
        Ticket t = tickets.findByTicketNumber(number.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Ticket " + number));
        return VehicleService.toDto(t, t.getVehicle());
    }

    // ------------------------------------------------------------ Tableau des enlèvements

    @Transactional(readOnly = true)
    public List<PickupColumn> board() {
        ParkQueryService.Snapshot s = query.snapshot();
        Map<PickupStage, List<PickupCard>> cols = new EnumMap<>(PickupStage.class);
        for (PickupStage st : PickupStage.values()) cols.put(st, new ArrayList<>());
        Instant startOfDay = Instant.now().minus(Duration.ofHours(24));
        for (Vehicle v : s.vehicles()) {
            PickupStage st = query.stage(v, s);
            if (st == PickupStage.SORTI && (v.getExitedAt() == null || v.getExitedAt().isBefore(startOfDay))) continue;
            List<String> missing = s.formalities(v).stream().filter(f -> !f.isReceived())
                    .map(f -> shortLabel(f.getType())).toList();
            Ticket t = s.lastTicketByVehicle().get(v.getId());
            String meta = switch (st) {
                case FORMALITES_EN_COURS -> query.summary(v, s).daysOnPark() + " j sur parc";
                case AUTORISE_TICKET_A_EMETTRE -> s.formalities(v).stream().filter(f -> f.getType() == FormalityType.BAE)
                        .findFirst().map(f -> f.getReceivedAt() == null ? "Autorisé" : "Autorisé depuis " + HM.format(f.getReceivedAt()))
                        .orElse("Autorisé");
                case TICKET_EMIS -> t == null ? "Ticket émis" : "Ticket " + t.getTicketNumber()
                        + (t.getSlot() == null ? " · valable 48 h" : " · créneau " + t.getSlot());
                case AU_PORTAIL -> "Contrôle en cours au portail";
                case SORTI -> "Sortie " + HM.format(v.getExitedAt()) + " · historique conservé";
            };
            cols.get(st).add(new PickupCard(v.getVin(), v.getShortCode(), v.getModel() + (v.getColor() == null ? "" : " · " + v.getColor()),
                    v.getExitedAt() != null ? "Sorti" : (v.getPlace() == null ? "—" : v.getPlace().label()),
                    "Client " + VehicleService.mask(v.getBeneficiaryName()), missing, meta,
                    st == PickupStage.AUTORISE_TICKET_A_EMETTRE, st == PickupStage.FORMALITES_EN_COURS));
        }
        cols.get(PickupStage.FORMALITES_EN_COURS).sort(Comparator.comparingInt((PickupCard c) -> -c.missing().size()));
        return List.of(
                column(PickupStage.FORMALITES_EN_COURS, "Formalités en cours", "Localisé, mais pas encore autorisé à sortir", cols),
                column(PickupStage.AUTORISE_TICKET_A_EMETTRE, "Autorisé · ticket à émettre", "Toutes les formalités sont validées", cols),
                column(PickupStage.TICKET_EMIS, "Ticket émis · client attendu", "Le client a reçu son ticket", cols),
                column(PickupStage.AU_PORTAIL, "Au portail · contrôle", "Véhicule, autorisation et bénéficiaire vérifiés", cols),
                column(PickupStage.SORTI, "Sortis (24 h)", "Historique complet conservé", cols));
    }

    private static PickupColumn column(PickupStage st, String title, String hint, Map<PickupStage, List<PickupCard>> cols) {
        List<PickupCard> all = cols.get(st);
        return new PickupColumn(st.name(), title, hint, all.size(), all.stream().limit(12).toList());
    }

    private static String shortLabel(FormalityType t) {
        return switch (t) {
            case BL -> "BL";
            case BON_LIVRAISON -> "BL armateur";
            case PAIEMENT -> "Paiement";
            case BAE -> "BAE";
            case BENEFICIAIRE -> "Bénéficiaire";
        };
    }
}
