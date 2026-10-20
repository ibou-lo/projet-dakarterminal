package sn.dakarterminal.autotrack.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sn.dakarterminal.autotrack.domain.AuditEvent;
import sn.dakarterminal.autotrack.domain.AuditEvent.Category;
import sn.dakarterminal.autotrack.repository.AuditEventRepository;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.web.dto.Dtos.AuditDto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Écriture et lecture du journal d'audit. Chaque écriture se fait dans sa propre transaction :
 * un échec de connexion ou un refus d'accès est journalisé même si l'opération échoue.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    /** Événement dont l'auteur est l'utilisateur connecté (ou anonyme). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Category category, String action, String label, String target, String details, boolean success) {
        AuthPrincipal p = currentPrincipal();
        write(category, action, label, p == null ? null : p.username(), p == null ? null : p.fullName(),
                p == null ? null : p.role().name(), target, details, success);
    }

    /** Événement dont l'auteur est connu explicitement (ex. tentative de connexion). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAs(String actor, String actorName, String actorRole, Category category, String action, String label,
                      String target, String details, boolean success) {
        write(category, action, label, actor, actorName, actorRole, target, details, success);
    }

    private void write(Category category, String action, String label, String actor, String actorName, String role,
                       String target, String details, boolean success) {
        try {
            AuditEvent e = new AuditEvent(category, action, label, actor, actorName, role, target, details, success);
            e.setIp(clientIp());
            events.save(e);
        } catch (RuntimeException ex) {
            // L'audit ne doit jamais bloquer l'application : on trace l'anomalie dans les journaux.
            log.warn("Audit non enregistré ({} {}) : {}", action, target, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditDto> search(String category, String action, String actor, String q, int days, int limit) {
        Instant since = Instant.now().minus(Duration.ofDays(Math.max(1, Math.min(days, 365))));
        String a = blank(action) ? null : action.trim().toUpperCase(Locale.ROOT);
        String c = blank(category) ? null : category.trim().toUpperCase(Locale.ROOT);
        String who = blank(actor) ? null : actor.trim().toLowerCase(Locale.ROOT);
        String text = blank(q) ? null : q.trim().toLowerCase(Locale.ROOT);
        return events.findSince(since, PageRequest.of(0, 5000)).stream()
                .filter(e -> c == null || e.getCategory().name().equals(c))
                .filter(e -> a == null || e.getAction().equals(a))
                .filter(e -> who == null || e.getActor().toLowerCase(Locale.ROOT).contains(who)
                        || (e.getActorName() != null && e.getActorName().toLowerCase(Locale.ROOT).contains(who)))
                .filter(e -> text == null || contains(e.getLabel(), text) || contains(e.getTarget(), text)
                        || contains(e.getDetails(), text))
                .limit(Math.max(1, Math.min(limit, 2000)))
                .map(AuditService::toDto)
                .toList();
    }

    public static AuditDto toDto(AuditEvent e) {
        return new AuditDto(e.getId(), e.getAt(), e.getActor(), e.getActorName(), e.getActorRole(), e.getCategory().name(),
                e.getAction(), e.getLabel(), e.getTarget(), e.getDetails(), e.getIp(), !Boolean.FALSE.equals(e.getSuccess()));
    }

    public static AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest req = attrs.getRequest();
        String fwd = req.getHeader("X-Forwarded-For");
        String ip = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
        return ip == null ? null : (ip.length() > 60 ? ip.substring(0, 60) : ip);
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
