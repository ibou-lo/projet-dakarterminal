package sn.dakarterminal.autotrack.assistant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.AssistantAction;
import sn.dakarterminal.autotrack.domain.Vehicle;
import sn.dakarterminal.autotrack.domain.Enums.FormalityType;
import sn.dakarterminal.autotrack.repository.AssistantActionRepository;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.*;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assistant à règles : reconnaît les intentions courantes du parc et répond à
 * partir des données (fiche, mouvements, formalités), en citant ses sources.
 * Les actions sont seulement PROPOSÉES ; un humain doit les confirmer.
 */
@Component
@Transactional
public class RuleBasedAssistantEngine implements AssistantEngine {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm").withZone(ZoneId.of("Africa/Dakar"));
    private static final Pattern VIN_LIKE = Pattern.compile("\\b(?:DAT-)?([A-HJ-NPR-Z0-9]{6,17})\\b");
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "Véhicules prêts depuis plus de 48 h ?",
            "Mouvements sans scan aujourd'hui",
            "Résume l'état à la réception");

    private final VehicleService vehicles;
    private final ParkQueryService query;
    private final MovementService movements;
    private final InventoryService inventory;
    private final AssistantActionRepository actions;

    public RuleBasedAssistantEngine(VehicleService vehicles, ParkQueryService query, MovementService movements,
                                    InventoryService inventory, AssistantActionRepository actions) {
        this.vehicles = vehicles;
        this.query = query;
        this.movements = movements;
        this.inventory = inventory;
        this.actions = actions;
    }

    @Override
    public ChatReply reply(ChatRequest req, AuthPrincipal user) {
        String raw = req.message().trim();
        String q = fold(raw);
        String vin = findVin(raw).orElse(req.contextVin());

        if (has(q, "place libre", "ou je gare", "ou garer", "garer")) return freePlace(user);
        if (has(q, "previen", "alerte", "previens")) return proposeBaeAlert(vin, q.contains("ticket"), user);
        if (has(q, "relance")) return proposeReminder(vin, user);
        if (has(q, "pret") && has(q, "48", "depuis", "pas encore enleve")) return readyForLong();
        if (has(q, "mouvement", "deplacement") && has(q, "scan", "sans", "manqu")) return missingScans(user);
        if (has(q, "incoheren", "ecart", "inventaire")) return incoherences(user);
        if (has(q, "etat", "reserve", "reception", "dommage")) return inspection(vin);
        if (vin != null && has(q, "ou ", "ou est", "manque", "trouve", "enlev", "statut", "pret", "localis", "?")) {
            return locate(vin);
        }
        if (vin != null) return locate(vin);
        return new ChatReply("Je réponds à partir des données enregistrées du parc. Vous pouvez me demander "
                + "où se trouve un véhicule (VIN, code court « DAT-… » ou 6 derniers caractères), ce qu'il manque "
                + "pour son enlèvement, les véhicules prêts non enlevés, les mouvements sans scan ou une place libre.",
                null, null, null, List.of(), DEFAULT_SUGGESTIONS);
    }

    // ------------------------------------------------------------------ intentions

    private ChatReply locate(String vin) {
        Passport p = passportOrThrow(vin);
        VehicleSummary v = p.vehicle();
        List<String> missing = p.formalities().stream().filter(f -> !"RECEIVED".equals(f.status())).map(FormalityDto::label).toList();
        StringBuilder sb = new StringBuilder();
        if ("SORTI".equals(v.placeCode())) {
            sb.append("Le véhicule ").append(v.vin()).append(" est sorti du terminal.");
        } else {
            sb.append("Il est en ").append(placeWords(v.placeCode()));
            if (v.lastScan() != null) {
                sb.append(", confirmé par scan le ").append(DMY.format(v.lastScan().at()))
                        .append(v.lastScan().by() == null ? "" : " par " + v.lastScan().by());
            }
            sb.append(". ");
            if ("POSITION_TO_CONFIRM".equals(v.status())) {
                sb.append("Attention : sa position est à confirmer (déplacement sans scan d'arrivée). ");
            }
            if (missing.isEmpty()) {
                sb.append("Toutes les formalités sont validées : il est prêt à enlever");
                sb.append(p.ticket() == null ? " et un ticket peut être généré." : " (ticket " + p.ticket().ticketNumber() + ").");
            } else if (missing.size() == 1) {
                sb.append("Il ne manque que : ").append(missing.get(0).toLowerCase(Locale.ROOT)).append(" ; le reste est validé.");
            } else {
                sb.append("Il manque : ").append(String.join(", ", missing)).append(".");
            }
        }
        return new ChatReply(sb.toString(), v, null, null,
                List.of("Fiche véhicule", "Journal des mouvements", "Formalités"),
                missing.contains(FormalityType.BAE.getLabel())
                        ? List.of("Préviens le transitaire dès que le BAE arrive, et prépare le ticket", "Résume l'état à la réception")
                        : DEFAULT_SUGGESTIONS);
    }

    private ChatReply inspection(String vin) {
        if (vin == null) return askVin();
        Passport p = passportOrThrow(vin);
        InspectionDto i = p.inspection();
        if (i == null) {
            return new ChatReply("Aucun état n'a encore été enregistré pour " + p.vehicle().shortCode() + ".",
                    p.vehicle(), null, null, List.of("Fiche de réception"), DEFAULT_SUGGESTIONS);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(i.photos().size()).append(" photos prises le ").append(DMY.format(i.inspectedAt()))
                .append(" par ").append(i.inspectedBy()).append(". ");
        if (i.reserves().isEmpty()) sb.append("Aucune réserve. ");
        else {
            sb.append(i.reserves().size()).append(" réserve(s) : ");
            i.reserves().forEach(r -> sb.append(r.description()).append(" (").append(r.location()).append(") "));
            sb.append(". ");
        }
        if (i.fuelLevel() != null) sb.append("Carburant ").append(i.fuelLevel()).append(", ");
        if (i.keysCount() != null) sb.append(i.keysCount()).append(" clé(s) remise(s). ");
        if (i.mileage() == null) sb.append("Kilométrage non saisi.");
        return new ChatReply(sb.toString().trim(), p.vehicle(), null, null, List.of("Fiche de réception"), DEFAULT_SUGGESTIONS);
    }

    private ChatReply readyForLong() {
        ParkQueryService.Snapshot s = query.snapshot();
        Instant limit = Instant.now().minus(Duration.ofHours(48));
        List<List<String>> rows = new ArrayList<>();
        s.vehicles().stream()
                .filter(v -> v.getExitedAt() == null)
                .filter(v -> query.status(v, s).name().equals("READY"))
                .map(v -> Map.<Vehicle, Instant>entry(v, s.formalities(v).stream().map(f -> f.getReceivedAt())
                        .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.now())))
                .filter(e -> e.getValue().isBefore(limit))
                .sorted(Map.Entry.<Vehicle, Instant>comparingByValue())
                .forEach(e -> rows.add(List.of("…" + e.getKey().getVin().substring(9),
                        e.getKey().getPlace() == null ? "—" : e.getKey().getPlace().label(),
                        Duration.between(e.getValue(), Instant.now()).toDays() + " jours")));
        String text = rows.isEmpty() ? "Aucun véhicule n'est prêt depuis plus de 48 h."
                : rows.size() + " véhicule(s) sont prêts depuis plus de 48 h. Les plus anciens sont en tête ; "
                + "je peux relancer leurs transitaires si vous le validez.";
        return new ChatReply(text, null, rows.isEmpty() ? null : new ChatTable(List.of("VIN", "Place", "Prêt depuis"),
                rows.stream().limit(8).toList()), null, List.of("Formalités", "Journal des mouvements"), DEFAULT_SUGGESTIONS);
    }

    private ChatReply missingScans(AuthPrincipal user) {
        List<MovementDto> list = movements.missingArrivals();
        List<List<String>> rows = list.stream().limit(10).map(m -> List.of("…" + m.vin().substring(9),
                VehicleService.label(m.fromPlace()), (m.agent() == null ? "?" : m.agent()) + " · " + DMY.format(m.startedAt()))).toList();
        String text = list.isEmpty() ? "Tous les déplacements du jour ont un scan d'arrivée."
                : list.size() + " déplacement(s) ont été démarrés sans scan de la place d'arrivée. Ces véhicules sont "
                + "marqués « position à confirmer » sur le plan.";
        ActionProposal action = null;
        if (!list.isEmpty()) {
            MovementDto first = list.get(0);
            action = propose("TACHE_VERIFICATION", first.vin(), first.fromPlace(),
                    "Envoyer une tâche de vérification pour " + first.shortCode() + " à l'agent le plus proche", user);
        }
        return new ChatReply(text, null, rows.isEmpty() ? null : new ChatTable(List.of("VIN", "Départ", "Agent"), rows),
                action, List.of("Journal des mouvements"), DEFAULT_SUGGESTIONS);
    }

    private ChatReply incoherences(AuthPrincipal user) {
        List<Incoherence> list = query.incoherences();
        List<List<String>> rows = list.stream().map(i -> List.of("…" + i.vin().substring(9),
                VehicleService.label(i.placeCode()), i.message())).toList();
        String text = list.isEmpty() ? "Aucune incohérence détectée sur les dernières 24 h."
                : list.size() + " incohérence(s) entre les lectures et les fiches.";
        ActionProposal action = list.isEmpty() ? null : propose("TACHE_VERIFICATION", list.get(0).vin(),
                list.get(0).placeCode(), "Créer une tâche de vérification : " + list.get(0).message(), user);
        return new ChatReply(text, null, rows.isEmpty() ? null : new ChatTable(List.of("VIN", "Place", "Écart"), rows),
                action, List.of("Lectures QR / RFID / rafale", "Fiches véhicules"), DEFAULT_SUGGESTIONS);
    }

    private ChatReply freePlace(AuthPrincipal user) {
        String zone = user.zone() == null ? "B" : user.zone();
        PlaceSuggestion s = inventory.suggest(zone);
        return new ChatReply("La place libre la plus proche est " + placeWords(s.placeCode()) + " ("
                + s.freePlacesInZone() + " places libres en zone " + zone + "). Scannez le QR de la place pour confirmer.",
                null, null, null, List.of("Plan du parc"), List.of("Mes tâches", "Signaler un dommage"));
    }

    private ChatReply proposeBaeAlert(String vin, boolean withTicket, AuthPrincipal user) {
        if (vin == null) return askVin();
        Passport p = passportOrThrow(vin);
        ActionProposal a = propose("ALERTE_BAE", p.vehicle().vin(), withTicket ? "AUTO_TICKET" : null,
                "Alerte « BAE reçu » sur " + p.vehicle().shortCode() + " → SMS et e-mail au transitaire"
                        + (withTicket ? " → génération automatique du ticket de retrait (valable 48 h)" : ""), user);
        return new ChatReply("Je propose l'action suivante. Rien n'est envoyé sans votre validation.",
                p.vehicle(), null, a, List.of("Fiche véhicule", "Formalités"), DEFAULT_SUGGESTIONS);
    }

    private ChatReply proposeReminder(String vin, AuthPrincipal user) {
        if (vin == null) return askVin();
        Passport p = passportOrThrow(vin);
        ActionProposal a = propose("RELANCE_TRANSITAIRE", p.vehicle().vin(), null,
                "Relancer le transitaire de " + p.vehicle().shortCode() + " pour les pièces manquantes", user);
        return new ChatReply("Je peux relancer le transitaire. Confirmez pour envoyer.", p.vehicle(), null, a,
                List.of("Formalités"), DEFAULT_SUGGESTIONS);
    }

    // ------------------------------------------------------------------ utilitaires

    private ChatReply askVin() {
        return new ChatReply("De quel véhicule s'agit-il ? Indiquez le VIN, le code court (DAT-…) ou ses 6 derniers caractères.",
                null, null, null, List.of(), DEFAULT_SUGGESTIONS);
    }

    private Passport passportOrThrow(String vin) {
        try {
            return vehicles.passport(vin);
        } catch (ApiException e) {
            throw ApiException.notFound("Aucun véhicule ne correspond à « " + vin + " »");
        }
    }

    private ActionProposal propose(String type, String vin, String param, String description, AuthPrincipal user) {
        AssistantAction a = new AssistantAction();
        a.setType(type);
        a.setVin(vin);
        a.setParam(param);
        a.setDescription(description);
        a.setProposedTo(user.fullName());
        a.setProposedAt(Instant.now());
        actions.save(a);
        return new ActionProposal(a.getId(), type, vin, description, a.getStatus().name());
    }

    private static String placeWords(String code) {
        if (code == null) return "position inconnue";
        String[] p = code.split("-");
        return p.length == 3 ? "zone " + p[0] + ", allée " + p[1] + ", place " + p[2] : code;
    }

    private Optional<String> findVin(String text) {
        Matcher m = VIN_LIKE.matcher(text.toUpperCase(Locale.ROOT).replace("…", " "));
        while (m.find()) {
            String token = m.group(1);
            if (token.chars().anyMatch(Character::isDigit) && token.chars().filter(Character::isDigit).count() >= 3) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('’', '\'');
    }

    private static boolean has(String q, String... words) {
        for (String w : words) if (q.contains(w)) return true;
        return false;
    }
}
