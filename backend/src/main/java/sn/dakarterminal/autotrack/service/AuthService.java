package sn.dakarterminal.autotrack.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.AppUser;
import sn.dakarterminal.autotrack.domain.AuditEvent.Category;
import sn.dakarterminal.autotrack.domain.Enums.Role;
import sn.dakarterminal.autotrack.domain.Vehicle;
import sn.dakarterminal.autotrack.repository.AppUserRepository;
import sn.dakarterminal.autotrack.repository.VehicleRepository;
import sn.dakarterminal.autotrack.security.JwtService;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.TokenResponse;
import sn.dakarterminal.autotrack.web.dto.Dtos.UserInfo;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentification : personnel (identifiant + mot de passe BCrypt) et client
 * (numéro de BL + code reçu par SMS). Limite simple des tentatives échouées.
 */
@Service
// noRollbackFor : le compteur d'échecs et le verrouillage sont enregistrés même quand la connexion est refusée.
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final VehicleRepository vehicles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final PrivilegeService privileges;
    /** Tentatives des clients (BL) : en mémoire, le compte n'existe pas en base. */
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> clientLockedUntil = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, VehicleRepository vehicles, PasswordEncoder encoder, JwtService jwt,
                       AuditService audit, PrivilegeService privileges) {
        this.users = users;
        this.vehicles = vehicles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.privileges = privileges;
    }

    /**
     * Connexion du personnel. Verrouillage du compte 15 min après 5 échecs consécutifs
     * (déverrouillage possible par un administrateur). Tout est journalisé.
     */
    public TokenResponse login(String username, String password) {
        String name = username.trim().toLowerCase();
        AppUser u = users.findByUsername(name).orElse(null);
        if (u == null) {
            audit.logAs(name, null, null, Category.SECURITE, "ECHEC_CONNEXION", "Échec de connexion", name,
                    "Identifiant inconnu", false);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect");
        }
        String role = u.getRole().name();
        if (u.isLocked()) {
            audit.logAs(name, u.getFullName(), role, Category.SECURITE, "CONNEXION_REFUSEE", "Connexion refusée", name,
                    "Compte verrouillé jusqu'à " + u.getLockedUntil(), false);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives : compte verrouillé 15 minutes (ou déverrouillage par un administrateur)");
        }
        if (!u.isEnabled()) {
            audit.logAs(name, u.getFullName(), role, Category.SECURITE, "CONNEXION_REFUSEE", "Connexion refusée", name,
                    "Compte désactivé", false);
            // Message générique : ne pas révéler l'existence ni l'état du compte (énumération)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect");
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            int n = u.failures() + 1;
            u.setFailedAttempts(n);
            boolean lock = n >= MAX_FAILURES;
            if (lock) u.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            audit.logAs(name, u.getFullName(), role, Category.SECURITE, lock ? "COMPTE_VERROUILLE" : "ECHEC_CONNEXION",
                    lock ? "Compte verrouillé (5 échecs)" : "Échec de connexion", name,
                    "Mot de passe incorrect · tentative " + n + "/" + MAX_FAILURES, false);
            throw new ApiException(lock ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED,
                    lock ? "Trop de tentatives : compte verrouillé 15 minutes" : "Identifiant ou mot de passe incorrect");
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(Instant.now());
        audit.logAs(name, u.getFullName(), role, Category.SECURITE, "CONNEXION", "Connexion", name,
                u.passwordChangeRequired() ? "Mot de passe provisoire : changement obligatoire" : null, true);
        return tokens(u.getUsername(), u.getFullName(), u.getRole(), u.getZone(), null, Instant.now(), u.passwordChangeRequired(),
                u.sessionVersion());
    }

    /** Informations de l'utilisateur connecté (privilèges à jour, changement de mot de passe requis). */
    @Transactional(readOnly = true)
    public UserInfo me(sn.dakarterminal.autotrack.security.AuthPrincipal p) {
        boolean mustChange = p.role() != Role.CLIENT && users.findByUsername(p.username())
                .map(AppUser::passwordChangeRequired).orElse(false);
        return new UserInfo(p.username(), p.fullName(), p.role().name(), p.zone(), p.bl(), privileges.names(p.role()), mustChange);
    }

    /** Déconnexion côté serveur : tous les jetons déjà émis pour ce compte deviennent invalides. */
    public void logout(String username) {
        users.findByUsername(username).ifPresent(u -> {
            u.revokeSessions();
            audit.log(Category.SECURITE, "DECONNEXION", "Déconnexion", username, null, true);
        });
    }

    /**
     * Changement de son propre mot de passe (mot de passe actuel exigé). Les autres sessions du compte
     * sont fermées ; de nouveaux jetons sont renvoyés pour la session en cours.
     */
    public TokenResponse changePassword(String username, String current, String next) {
        AppUser u = users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Compte introuvable"));
        if (!encoder.matches(current, u.getPasswordHash())) {
            audit.log(Category.SECURITE, "MOT_DE_PASSE_REFUSE", "Changement de mot de passe refusé", username,
                    "Mot de passe actuel incorrect", false);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }
        AdminService.checkPasswordPolicy(next, username);
        if (encoder.matches(next, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe doit être différent de l'actuel");
        }
        u.setPasswordHash(encoder.encode(next));
        u.setPasswordChangedAt(Instant.now());
        u.setMustChangePassword(false);
        u.revokeSessions();
        audit.log(Category.SECURITE, "MOT_DE_PASSE_CHANGE", "Mot de passe changé", username,
                "Autres sessions du compte fermées", true);
        return tokens(u.getUsername(), u.getFullName(), u.getRole(), u.getZone(), null, Instant.now(), false, u.sessionVersion());
    }

    public TokenResponse clientLogin(String blNumber, String smsCode) {
        String bl = blNumber.trim().toUpperCase();
        String key = "c:" + bl;
        guard(key);
        Vehicle v = vehicles.findByBlNumber(bl).orElse(null);
        boolean ok = v != null && v.getClientAccessCode() != null && MessageDigest.isEqual(
                v.getClientAccessCode().getBytes(StandardCharsets.UTF_8),
                smsCode.trim().replace(" ", "").getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            failures.merge(key, 1, Integer::sum);
            if (failures.get(key) >= MAX_FAILURES) clientLockedUntil.put(key, Instant.now().plus(LOCK_DURATION));
            audit.logAs("client:" + bl, null, "CLIENT", Category.SECURITE, "ECHEC_CONNEXION", "Échec de connexion client", bl,
                    v == null ? "BL inconnu" : "Code SMS incorrect", false);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Numéro de BL ou code incorrect");
        }
        failures.remove(key);
        clientLockedUntil.remove(key);
        audit.logAs("client:" + bl, VehicleService.mask(v.getBeneficiaryName()), "CLIENT", Category.SECURITE,
                "CONNEXION", "Connexion client", bl, null, true);
        return tokens("client:" + bl, VehicleService.mask(v.getBeneficiaryName()), Role.CLIENT, null, bl, Instant.now(), false, 0);
    }

    public TokenResponse refresh(String refreshToken) {
        try {
            Claims c = jwt.parse(refreshToken);
            if (!JwtService.TYPE_REFRESH.equals(c.get(JwtService.CLAIM_TYPE, String.class))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Jeton de rafraîchissement attendu");
            }
            // Durée de session absolue : pas de rafraîchissement au-delà de 12 h après la connexion
            Long authTimeSec = c.get(JwtService.CLAIM_AUTH_TIME) instanceof Number n ? n.longValue() : null;
            Instant authTime = authTimeSec == null ? c.getIssuedAt().toInstant() : Instant.ofEpochSecond(authTimeSec);
            if (authTime.plus(jwt.refreshTtl()).isBefore(Instant.now())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expirée, reconnectez-vous");
            }
            Role role = Role.valueOf(c.get(JwtService.CLAIM_ROLE, String.class));
            if (role != Role.CLIENT) {
                // Compte désactivé, déconnecté ou mot de passe changé : plus de rafraîchissement.
                AppUser u = users.findByUsername(c.getSubject()).filter(AppUser::isEnabled)
                        .filter(x -> x.sessionVersion() == JwtService.sessionVersionOf(c))
                        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expirée, reconnectez-vous"));
                return tokens(u.getUsername(), u.getFullName(), u.getRole(), u.getZone(), null, authTime,
                        u.passwordChangeRequired(), u.sessionVersion());
            }
            return tokens(c.getSubject(), c.get(JwtService.CLAIM_NAME, String.class), role,
                    c.get(JwtService.CLAIM_ZONE, String.class), c.get(JwtService.CLAIM_BL, String.class), authTime, false, 0);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expirée, reconnectez-vous");
        }
    }

    private void guard(String key) {
        Instant until = clientLockedUntil.get(key);
        if (until != null && until.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives : réessayez dans 15 minutes");
        }
        if (until != null) {           // verrouillage échu : nouveau cycle de 5 essais
            clientLockedUntil.remove(key);
            failures.remove(key);
        }
    }


    private TokenResponse tokens(String subject, String name, Role role, String zone, String bl, Instant authTime,
                                 boolean mustChangePassword, int sessionVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtService.CLAIM_SESSION_VERSION, sessionVersion);
        claims.put(JwtService.CLAIM_AUTH_TIME, authTime.getEpochSecond());
        claims.put(JwtService.CLAIM_ROLE, role.name());
        claims.put(JwtService.CLAIM_NAME, name);
        if (zone != null) claims.put(JwtService.CLAIM_ZONE, zone);
        if (bl != null) claims.put(JwtService.CLAIM_BL, bl);
        return new TokenResponse(jwt.createAccessToken(subject, claims), jwt.createRefreshToken(subject, claims),
                jwt.accessTtlSeconds(), new UserInfo(subject, name, role.name(), zone, bl, privileges.names(role), mustChangePassword));
    }
}
