package sn.dakarterminal.autotrack.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.AppUser;
import sn.dakarterminal.autotrack.domain.AuditEvent.Category;
import sn.dakarterminal.autotrack.domain.Enums.Role;
import sn.dakarterminal.autotrack.repository.AppUserRepository;
import sn.dakarterminal.autotrack.repository.AuditEventRepository;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Gestion des comptes du personnel : création, modification du profil, désactivation,
 * déverrouillage, mot de passe provisoire. Chaque action est journalisée.
 * Garde-fous : on ne peut ni se désactiver ni se retirer le profil administrateur,
 * et il reste toujours au moins un administrateur actif.
 */
@Service
@Transactional
public class AdminService {

    /** Profils attribuables à un compte du personnel (le profil CLIENT est réservé à l'espace client). */
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.SUPERVISEUR, Role.AGENT, Role.PORTAIL);
    private static final String PWD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final AuditEventRepository events;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminService(AppUserRepository users, AuditEventRepository events, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.events = events;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> list() {
        return users.findAllByOrderByRoleAscUsernameAsc().stream().map(AdminService::toDto).toList();
    }

    public UserWithPassword create(UserCreateRequest r, AuthPrincipal me) {
        String username = r.username().trim().toLowerCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent()) {
            throw ApiException.conflict("L'identifiant « " + username + " » existe déjà");
        }
        Role role = staffRole(r.role());
        String pwd = temporaryPassword();
        AppUser u = new AppUser(username, encoder.encode(pwd), r.fullName().trim(), role, zone(r.zone()));
        u.setEmail(blankToNull(r.email()));
        u.setPhone(blankToNull(r.phone()));
        u.setCreatedBy(me.fullName());
        u.setMustChangePassword(true);   // mot de passe provisoire : à changer à la première connexion
        users.save(u);
        audit.log(Category.ADMINISTRATION, "UTILISATEUR_CREE", "Compte créé", username,
                "Profil " + role.name() + (u.getZone() == null ? "" : " · zone " + u.getZone()), true);
        return new UserWithPassword(toDto(u), pwd);
    }

    public AdminUserDto update(String username, UserUpdateRequest r, AuthPrincipal me) {
        AppUser u = require(username);
        Role newRole = staffRole(r.role());
        if (u.getUsername().equals(me.username()) && newRole != u.getRole()) {
            throw ApiException.badRequest("Vous ne pouvez pas modifier votre propre profil");
        }
        if (u.getRole() == Role.ADMIN && newRole != Role.ADMIN) ensureAnotherAdmin(u);
        List<String> changes = new ArrayList<>();
        if (newRole != u.getRole()) changes.add("profil " + u.getRole() + " → " + newRole);
        String z = zone(r.zone());
        if (!Objects.equals(z, u.getZone())) changes.add("zone " + dash(u.getZone()) + " → " + dash(z));
        if (!Objects.equals(r.fullName().trim(), u.getFullName())) changes.add("nom « " + u.getFullName() + " » → « " + r.fullName().trim() + " »");
        if (!Objects.equals(blankToNull(r.email()), u.getEmail())) changes.add("e-mail");
        if (!Objects.equals(blankToNull(r.phone()), u.getPhone())) changes.add("téléphone");
        u.setRole(newRole);
        u.setZone(z);
        u.setFullName(r.fullName().trim());
        u.setEmail(blankToNull(r.email()));
        u.setPhone(blankToNull(r.phone()));
        if (!changes.isEmpty()) {
            boolean roleChanged = changes.get(0).startsWith("profil");
            audit.log(Category.ADMINISTRATION, roleChanged ? "PROFIL_MODIFIE" : "UTILISATEUR_MODIFIE",
                    roleChanged ? "Profil modifié" : "Compte modifié", u.getUsername(), String.join(" · ", changes), true);
        }
        return toDto(u);
    }

    public AdminUserDto setEnabled(String username, boolean enabled, AuthPrincipal me) {
        AppUser u = require(username);
        if (!enabled && u.getUsername().equals(me.username())) {
            throw ApiException.badRequest("Vous ne pouvez pas désactiver votre propre compte");
        }
        if (!enabled && u.getRole() == Role.ADMIN) ensureAnotherAdmin(u);
        if (u.isEnabled() != enabled) {
            u.setEnabled(enabled);
            if (!enabled) u.revokeSessions();
            audit.log(Category.ADMINISTRATION, enabled ? "COMPTE_REACTIVE" : "COMPTE_DESACTIVE",
                    enabled ? "Compte réactivé" : "Compte désactivé", u.getUsername(),
                    enabled ? null : "Sessions en cours coupées immédiatement", true);
        }
        return toDto(u);
    }

    public AdminUserDto unlock(String username) {
        AppUser u = require(username);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        audit.log(Category.ADMINISTRATION, "COMPTE_DEVERROUILLE", "Compte déverrouillé", u.getUsername(), null, true);
        return toDto(u);
    }

    public UserWithPassword resetPassword(String username) {
        AppUser u = require(username);
        String pwd = temporaryPassword();
        u.setPasswordHash(encoder.encode(pwd));
        u.setPasswordChangedAt(Instant.now());
        u.setMustChangePassword(true);
        u.revokeSessions(); // sessions en cours fermées
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        audit.log(Category.ADMINISTRATION, "MOT_DE_PASSE_REINITIALISE", "Mot de passe réinitialisé", u.getUsername(),
                "Mot de passe provisoire remis à l'utilisateur", true);
        return new UserWithPassword(toDto(u), pwd);
    }

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        List<AppUser> all = users.findAll();
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role r : List.of(Role.ADMIN, Role.SUPERVISEUR, Role.AGENT, Role.PORTAIL)) {
            byRole.put(r.name(), all.stream().filter(u -> u.getRole() == r).count());
        }
        Instant day = Instant.now().minus(Duration.ofHours(24));
        long changes = audit.search("ADMINISTRATION", null, null, null, 7, 2000).size();
        return new AdminOverview(all.size(), all.stream().filter(AppUser::isEnabled).count(),
                all.stream().filter(AppUser::isLocked).count(), byRole,
                events.countByActionAndAtAfter("CONNEXION", day),
                events.countByActionAndAtAfter("ECHEC_CONNEXION", day),
                events.countByActionAndAtAfter("ACCES_REFUSE", day),
                changes,
                audit.search("SECURITE", null, null, null, 7, 8));
    }

    // ------------------------------------------------------------------ utilitaires

    private void ensureAnotherAdmin(AppUser u) {
        long activeAdmins = users.countByRoleAndEnabledTrue(Role.ADMIN);
        if (activeAdmins <= 1 && u.isEnabled()) {
            throw ApiException.conflict("Il doit rester au moins un administrateur actif");
        }
    }

    private AppUser require(String username) {
        return users.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Utilisateur " + username));
    }

    private static Role staffRole(String role) {
        try {
            Role r = Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
            if (!STAFF_ROLES.contains(r)) throw new IllegalArgumentException();
            return r;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Profil attendu : ADMIN, SUPERVISEUR, AGENT ou PORTAIL");
        }
    }

    /** Mot de passe provisoire lisible (sans 0/O ni 1/l) : « Dat-xxxx-xxxx ». */
    static String temporaryPassword() {
        StringBuilder sb = new StringBuilder("Dat-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(PWD_CHARS.charAt(RANDOM.nextInt(PWD_CHARS.length())));
        }
        return sb.append(RANDOM.nextInt(10)).toString();
    }

    public static AdminUserDto toDto(AppUser u) {
        return new AdminUserDto(u.getUsername(), u.getFullName(), u.getRole().name(), u.getZone(), u.getEmail(), u.getPhone(),
                u.isEnabled(), u.isLocked(), u.failures(), u.getLockedUntil(), u.getCreatedAt(), u.getCreatedBy(),
                u.getLastLoginAt(), u.getPasswordChangedAt());
    }

    private static String zone(String z) {
        return z == null || z.isBlank() ? null : z.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String dash(String s) {
        return s == null ? "—" : s;
    }

    /** Mots de passe trop courants refusés (extrait ; à compléter par une liste de mots de passe compromis). */
    private static final Set<String> COMMON = Set.of("motdepasse", "password", "azerty", "qwerty", "123456", "dakar",
            "senegal", "terminal", "autotrack", "admin", "bienvenue", "soleil");

    /**
     * Règle de mot de passe choisi par l'utilisateur (OWASP ASVS V2.1 / recommandations ANSSI) :
     * 12 caractères minimum, 128 maximum, lettres et chiffres, sans l'identifiant ni un mot trop courant.
     */
    public static void checkPasswordPolicy(String pwd, String username) {
        if (pwd == null || pwd.length() < 12 || pwd.length() > 128 || !pwd.matches(".*[A-Za-z].*") || !pwd.matches(".*\\d.*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Mot de passe : 12 caractères minimum, avec au moins une lettre et un chiffre");
        }
        String low = pwd.toLowerCase(Locale.ROOT);
        String user = username == null ? "" : username.toLowerCase(Locale.ROOT);
        String userPart = user.contains(".") ? user.substring(user.indexOf('.') + 1) : user;
        if ((!user.isEmpty() && low.contains(user)) || (userPart.length() >= 3 && low.contains(userPart))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le mot de passe ne doit pas contenir votre identifiant");
        }
        String letters = low.replaceAll("[^a-z]", "");
        if (COMMON.stream().anyMatch(letters::equals)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mot de passe trop courant : choisissez une phrase ou une combinaison moins prévisible");
        }
    }
}
