package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Journal d'audit : connexions, refus d'accès, gestion des comptes et actions métier
 * (réception, ticket, décision au portail…). Écrit uniquement par le serveur, jamais modifié.
 */
@Entity
@Table(name = "audit_event", indexes = {@Index(columnList = "at"), @Index(columnList = "actor")})
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    /** SECURITE (connexions, refus), ADMINISTRATION (comptes), OPERATION (actions métier). */
    public enum Category { SECURITE, ADMINISTRATION, OPERATION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_at", nullable = false)
    private Instant at;

    /** Identifiant de la personne à l'origine de l'événement (ou « anonyme »). */
    @Column(nullable = false, length = 80)
    private String actor;

    @Column(length = 120)
    private String actorName;

    @Column(length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    /** Code stable, ex. CONNEXION, ECHEC_CONNEXION, UTILISATEUR_CREE, TICKET_EMIS. */
    @Column(nullable = false, length = 40)
    private String action;

    /** Libellé lisible, ex. « Ticket de retrait émis ». */
    @Column(nullable = false, length = 120)
    private String label;

    /** Objet concerné : identifiant d'utilisateur, VIN, zone, n° de ticket… */
    @Column(length = 120)
    private String target;

    @Column(length = 500)
    private String details;

    @Column(length = 60)
    private String ip;

    private Boolean success;

    public AuditEvent(Category category, String action, String label, String actor, String actorName, String actorRole,
                      String target, String details, boolean success) {
        this.at = Instant.now();
        this.category = category;
        this.action = action;
        this.label = label;
        this.actor = actor == null ? "anonyme" : actor;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.target = target;
        this.details = details == null ? null : (details.length() > 500 ? details.substring(0, 497) + "…" : details);
        this.success = success;
    }
}
