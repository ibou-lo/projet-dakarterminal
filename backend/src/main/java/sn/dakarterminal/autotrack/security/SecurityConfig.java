package sn.dakarterminal.autotrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import sn.dakarterminal.autotrack.config.AutoTrackProperties;
import sn.dakarterminal.autotrack.domain.AuditEvent;
import sn.dakarterminal.autotrack.service.AuditService;

import java.util.List;
import java.util.Map;

/**
 * Sécurité sans session : chaque appel porte un JWT.
 * Les droits fins par rôle sont posés sur les contrôleurs (@PreAuthorize).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] STAFF = {"ADMIN", "SUPERVISEUR", "AGENT", "PORTAIL"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper mapper, AuditService audit,
                                           @Value("${spring.h2.console.enabled:false}") boolean h2Console) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // En-têtes de sécurité (OWASP ASVS V14.4) : nosniff, cache-control et HSTS (en HTTPS) sont posés
                // par Spring Security ; on ajoute CSP stricte sur l'API, anti-cadrage, Referrer et Permissions-Policy.
                .headers(h -> {
                    if (h2Console) h.frameOptions(f -> f.sameOrigin()); // console H2 (profil h2 uniquement)
                    else h.frameOptions(f -> f.deny());
                    h.httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31_536_000));
                    h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    h.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
                    h.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(antMatcher("/api/**"),
                            new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")));
                })
                // Matchers « Ant » explicites : la console H2 ajoute une seconde servlet,
                // ce qui rend ambigus les motifs sous forme de simples chaînes.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(antMatcher(HttpMethod.OPTIONS, "/**")).permitAll()
                        .requestMatchers(antMatcher("/api/auth/login"), antMatcher("/api/auth/refresh"),
                                antMatcher("/api/client/login"), antMatcher("/api/health")).permitAll()
                        .requestMatchers(antMatcher("/swagger-ui.html"), antMatcher("/swagger-ui/**"),
                                antMatcher("/v3/api-docs/**"), antMatcher("/h2-console/**")).permitAll()
                        .requestMatchers(antMatcher("/api/client/**")).hasRole("CLIENT")
                        // Personnel (tous profils) : l'accès fin est porté par les PRIVILÈGES (security.Privilege),
                        // configurables par profil depuis l'administration (Profils & privilèges).
                        .requestMatchers(antMatcher("/api/admin/**"), antMatcher("/api/auth/me"), antMatcher("/api/auth/password"),
                                antMatcher("/api/auth/logout"))
                        .hasAnyRole(STAFF)
                        // Données propres à un écran : privilège de la page (les écritures sont contrôlées par @PreAuthorize)
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/gate/**")).hasAuthority("PAGE_PORTIQUE")
                        .requestMatchers(antMatcher("/api/indicators/**")).hasAuthority("PAGE_INDICATEURS")
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/movements/**")).hasAuthority("PAGE_MOUVEMENTS")
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/tasks/**"))
                        .hasAnyAuthority("PAGE_MOUVEMENTS", "PAGE_FRAICHEUR", "TACHE_GERER")
                        .requestMatchers(antMatcher("/api/pickups/**")).hasAuthority("PAGE_ENLEVEMENTS")
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/tags/**"), antMatcher(HttpMethod.GET, "/api/labels/**"))
                        .hasAnyAuthority("PAGE_ETIQUETTES", "ETIQUETTE_ASSOCIER")
                        .requestMatchers(antMatcher("/api/park/incoherences")).hasAuthority("PAGE_FRAICHEUR")
                        // Données communes (parc, véhicules, zones, tickets, clés…) : tout le personnel
                        .requestMatchers(antMatcher("/api/**")).hasAnyRole(STAFF)
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(), Map.of("error", "unauthorized",
                                    "message", "Authentification requise ou jeton expiré"));
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            audit.log(AuditEvent.Category.SECURITE, "ACCES_REFUSE", "Accès refusé",
                                    req.getMethod() + " " + req.getRequestURI(), "Droits insuffisants pour ce profil", false);
                            res.setStatus(403);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(), Map.of("error", "forbidden",
                                    "message", "Droits insuffisants pour ce rôle"));
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AutoTrackProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        // Motifs : autorise l'app web Angular (4200) et Flutter web (port aléatoire) en local.
        cfg.setAllowedOriginPatterns(props.security().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
