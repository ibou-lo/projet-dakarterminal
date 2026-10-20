package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.Role;

import java.time.Instant;

/** Utilisateur du terminal (administrateur, superviseur, agent de parc, agent de portail). */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    /** Mot de passe haché (BCrypt). */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Zone d'affectation (ex. « B »), utilisée pour limiter l'accès de l'assistant. */
    @Column(length = 5)
    private String zone;

    private boolean enabled = true;

    // Champs ajoutés pour l'administration : types « objet » (nullables) pour que la mise à jour
    // automatique du schéma fonctionne aussi sur une base PostgreSQL déjà remplie.
    @Column(length = 120)
    private String email;

    @Column(length = 30)
    private String phone;

    private Instant createdAt;

    @Column(length = 120)
    private String createdBy;

    private Instant lastLoginAt;

    private Instant passwordChangedAt;

    /** Échecs de connexion consécutifs ; au 5e, le compte est verrouillé 15 minutes. */
    private Integer failedAttempts;

    private Instant lockedUntil;

    /**
     * Version de session : incrémentée à la déconnexion, au changement / à la réinitialisation du mot de passe
     * et à la désactivation. Les jetons portant une version antérieure sont refusés.
     */
    private Integer sessionVersion;

    public int sessionVersion() {
        return sessionVersion == null ? 0 : sessionVersion;
    }

    /** Invalide tous les jetons déjà émis pour ce compte. */
    public void revokeSessions() {
        sessionVersion = sessionVersion() + 1;
    }

    /** Mot de passe provisoire (création / réinitialisation) : changement obligatoire avant tout accès. */
    private Boolean mustChangePassword;

    public boolean passwordChangeRequired() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public int failures() {
        return failedAttempts == null ? 0 : failedAttempts;
    }

    public AppUser(String username, String passwordHash, String fullName, Role role, String zone) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.zone = zone;
        this.createdAt = Instant.now();
        this.passwordChangedAt = this.createdAt;
    }
}
