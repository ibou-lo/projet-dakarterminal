package sn.dakarterminal.autotrack.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sn.dakarterminal.autotrack.domain.AuditEvent.Category;
import sn.dakarterminal.autotrack.service.AuditService;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation du débit des routes d'authentification par adresse IP (OWASP API4:2023, ASVS V2.2.1) :
 * complète le verrouillage par compte contre les attaques par force brute et la pulvérisation de mots de passe.
 * Fenêtre fixe de 5 minutes, en mémoire (prototype mono-instance ; en production : passerelle / WAF ou Redis).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 20,
            "/api/client/login", 10,
            "/api/auth/refresh", 60);

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AuditService audit;

    public LoginRateLimitFilter(AuditService audit) {
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        String key = path + "|" + req.getRemoteAddr();
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() > WINDOW_MS ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (w.count() > LIMITS.get(path)) {
            if (w.count() == LIMITS.get(path) + 1) {
                audit.logAs(req.getRemoteAddr(), null, null, Category.SECURITE, "LIMITE_DEBIT", "Trop de requêtes d'authentification",
                        path, "Plus de " + LIMITS.get(path) + " requêtes en 5 minutes depuis cette adresse", false);
            }
            long retry = Math.max(1, (w.start() + WINDOW_MS - now) / 1000);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retry));
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"Trop de tentatives depuis ce poste : réessayez dans quelques minutes\"}");
            return;
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> now - e.getValue().start() > WINDOW_MS);
        chain.doFilter(req, res);
    }
}
