package sn.dakarterminal.autotrack.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import sn.dakarterminal.autotrack.domain.AuditEvent.Category;
import sn.dakarterminal.autotrack.service.AuditService;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Journalise automatiquement les actions métier du personnel (toute requête d'écriture de l'API)
 * ainsi que les refus d'accès renvoyés par les contrôles @PreAuthorize.
 * Les connexions et la gestion des comptes sont journalisées plus finement par leurs services.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private record Rule(String method, Pattern path, String action, String label) {
    }

    private static Rule r(String method, String regex, String action, String label) {
        return new Rule(method, Pattern.compile("^/api" + regex + "$"), action, label);
    }

    /** Actions reconnues : (méthode, chemin) → code et libellé. Le groupe 1 du motif désigne l'objet concerné. */
    private static final List<Rule> RULES = List.of(
            r("POST", "/vehicles()", "RECEPTION", "Réception d'un véhicule"),
            r("PUT", "/vehicles/([^/]+)/inspection", "ETAT_ENREGISTRE", "État du véhicule enregistré"),
            r("POST", "/vehicles/([^/]+)/photos", "PHOTO_AJOUTEE", "Photo ajoutée"),
            r("POST", "/vehicles/([^/]+)/park", "STATIONNEMENT", "Stationnement confirmé par scan"),
            r("POST", "/vehicles/([^/]+)/assign", "AFFECTATION", "Affectation depuis le poste web"),
            r("POST", "/vehicles/([^/]+)/move", "DEPLACEMENT", "Déplacement démarré"),
            r("POST", "/vehicles/([^/]+)/formalities/[^/]+/receive", "FORMALITE_RECUE", "Formalité reçue (simulation)"),
            r("POST", "/vehicles/([^/]+)/formalities/[^/]+/reset", "FORMALITE_REINITIALISEE", "Formalité réinitialisée"),
            r("POST", "/vehicles/([^/]+)/ticket", "TICKET_EMIS", "Ticket de retrait émis"),
            r("POST", "/vehicles/([^/]+)/remind", "RELANCE_TRANSITAIRE", "Relance du transitaire"),
            r("POST", "/vehicles/([^/]+)/tag", "ETIQUETTE_ASSOCIEE", "Étiquette RFID associée"),
            r("POST", "/keys/([^/]+)", "MOUVEMENT_CLE", "Mouvement de clé"),
            r("POST", "/gate/scan()", "SCAN_PORTAIL", "Scan au portail de sortie"),
            r("POST", "/gate/passages/([^/]+)/decision", "DECISION_SORTIE", "Décision de sortie"),
            r("POST", "/park/sweep()", "INVENTAIRE_RAFALE", "Inventaire en rafale"),
            r("POST", "/park/zone-read()", "LECTURE_PORTIQUE_ZONE", "Lecture d'un portique de zone"),
            r("POST", "/zones()", "ZONE_CREEE", "Zone créée"),
            r("PUT", "/zones/([^/]+)", "ZONE_MODIFIEE", "Zone modifiée"),
            r("POST", "/zones/([^/]+)/allees", "ALLEES_AJOUTEES", "Allées ajoutées"),
            r("DELETE", "/zones/([^/]+)", "ZONE_SUPPRIMEE", "Zone supprimée"),
            r("POST", "/tasks()", "TACHE_CREEE", "Tâche de vérification créée"),
            r("POST", "/tasks/([^/]+)/done", "TACHE_TERMINEE", "Tâche de vérification terminée"),
            r("POST", "/assistant/actions/([^/]+)", "ACTION_IA", "Action proposée par l'IA validée ou annulée"),
            r("POST", "/client/notify()", "ALERTE_CLIENT", "Alerte SMS demandée par le client"));

    private final AuditService audit;

    public AuditInterceptor(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        String uri = req.getRequestURI();
        if (!uri.startsWith("/api/")) return;
        int status = res.getStatus();
        if (status == 403) {
            audit.log(Category.SECURITE, "ACCES_REFUSE", "Accès refusé", req.getMethod() + " " + uri,
                    "Action non autorisée pour ce profil", false);
            return;
        }
        // Connexions et gestion des comptes : journalisées plus finement par leurs services
        if (uri.startsWith("/api/admin/") || uri.startsWith("/api/auth/")) return;
        String method = req.getMethod();
        if ("GET".equals(method) || "OPTIONS".equals(method) || "HEAD".equals(method) || status == 401) return;
        String path = uri.substring(4); // sans « /api »
        for (Rule rule : RULES) {
            if (!rule.method().equals(method)) continue;
            Matcher m = rule.path().matcher("/api" + path);
            if (m.matches()) {
                String target = m.groupCount() > 0 && !m.group(1).isEmpty() ? m.group(1) : null;
                boolean ok = status < 400 && ex == null;
                audit.log(Category.OPERATION, rule.action(), rule.label(), target, ok ? null : "Refusé (HTTP " + status + ")", ok);
                return;
            }
        }
    }
}
