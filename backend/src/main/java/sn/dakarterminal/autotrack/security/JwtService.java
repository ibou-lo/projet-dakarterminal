package sn.dakarterminal.autotrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import sn.dakarterminal.autotrack.config.AutoTrackProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Émission et vérification des jetons JWT (HS256).
 * - jeton d'accès court (60 min par défaut) ;
 * - jeton de rafraîchissement plus long (12 h), refusé pour appeler l'API.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ZONE = "zone";
    public static final String CLAIM_BL = "bl";
    public static final String CLAIM_TYPE = "typ";
    /** Heure de la connexion initiale (secondes) : conservée au rafraîchissement → durée de session absolue. */
    public static final String CLAIM_AUTH_TIME = "auth_time";
    /** Version de session du compte au moment de l'émission (révocation des jetons). */
    public static final String CLAIM_SESSION_VERSION = "sv";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    private static final String ISSUER = "dakar-autotrack";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(AutoTrackProperties props) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("autotrack.security.jwt-secret doit contenir au moins 32 octets");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.security().accessTokenMinutes());
        this.refreshTtl = Duration.ofHours(props.security().refreshTokenHours());
    }

    public String createAccessToken(String subject, Map<String, Object> claims) {
        return build(subject, claims, TYPE_ACCESS, accessTtl);
    }

    public String createRefreshToken(String subject, Map<String, Object> claims) {
        return build(subject, claims, TYPE_REFRESH, refreshTtl);
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private String build(String subject, Map<String, Object> claims, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(subject)
                .claims(claims)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Version de session portée par le jeton (0 si absente). */
    public static int sessionVersionOf(Claims c) {
        return c.get(CLAIM_SESSION_VERSION) instanceof Number n ? n.intValue() : 0;
    }

    /** Vérifie la signature, l'émetteur et l'expiration ; lève JwtException sinon. */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
