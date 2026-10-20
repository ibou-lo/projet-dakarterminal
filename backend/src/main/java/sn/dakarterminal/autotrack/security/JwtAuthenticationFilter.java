package sn.dakarterminal.autotrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sn.dakarterminal.autotrack.domain.AppUser;
import sn.dakarterminal.autotrack.domain.Enums.Role;
import sn.dakarterminal.autotrack.repository.AppUserRepository;
import sn.dakarterminal.autotrack.service.PrivilegeService;
import org.springframework.security.core.GrantedAuthority;
import java.util.ArrayList;

import java.io.IOException;
import java.util.List;

/**
 * Lit l'en-tête « Authorization: Bearer … », valide le JWT d'accès et
 * alimente le contexte de sécurité. Un jeton invalide laisse la requête
 * anonyme : les règles de SecurityConfig renverront 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;
    private final PrivilegeService privileges;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users, PrivilegeService privileges) {
        this.jwtService = jwtService;
        this.users = users;
        this.privileges = privileges;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if (JwtService.TYPE_ACCESS.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
                    Role role = Role.valueOf(claims.get(JwtService.CLAIM_ROLE, String.class));
                    // Compte désactivé, profil modifié, déconnexion ou mot de passe changé : le jeton n'est plus
                    // accepté (sans attendre son expiration). Les clients n'ont pas de compte en base.
                    AppUser account = null;
                    if (role != Role.CLIENT) {
                        account = stillValid(claims.getSubject(), role, JwtService.sessionVersionOf(claims));
                        if (account == null) {
                            chain.doFilter(request, response);
                            return;
                        }
                    }
                    // Mot de passe provisoire : seules les routes du compte restent ouvertes (ASVS 2.3.1)
                    if (account != null && account.passwordChangeRequired() && !request.getRequestURI().startsWith("/api/auth/")) {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"password_change_required\","
                                + "\"message\":\"Changement du mot de passe provisoire obligatoire\"}");
                        return;
                    }
                    AuthPrincipal principal = new AuthPrincipal(
                            claims.getSubject(),
                            claims.get(JwtService.CLAIM_NAME, String.class),
                            role,
                            claims.get(JwtService.CLAIM_ZONE, String.class),
                            claims.get(JwtService.CLAIM_BL, String.class));
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities(role));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /** Compte actif, même profil, jeton de la version de session en cours ; sinon null. */
    private AppUser stillValid(String username, Role role, int sessionVersion) {
        return users.findByUsername(username)
                .filter(AppUser::isEnabled)
                .filter(u -> u.getRole() == role)
                .filter(u -> u.sessionVersion() == sessionVersion)
                .orElse(null);
    }

    /** Autorités : ROLE_<profil> + un privilège par autorité (ex. TICKET_EMETTRE), lus dans le cache à jour. */
    private List<GrantedAuthority> authorities(Role role) {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (Privilege p : privileges.of(role)) list.add(new SimpleGrantedAuthority(p.name()));
        return list;
    }
}
