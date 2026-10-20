package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.ActionStatus;

import java.time.Instant;

/**
 * Action proposée par l'assistant IA. Rien n'est exécuté tant qu'un humain
 * ne l'a pas confirmée ; chaque décision est journalisée.
 */
@Entity
@Table(name = "assistant_action")
@Getter
@Setter
@NoArgsConstructor
public class AssistantAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ALERTE_BAE, RELANCE_TRANSITAIRE, CORRIGER_POSITION, TACHE_VERIFICATION. */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 17)
    private String vin;

    /** Paramètre complémentaire (ex. nouvelle place pour CORRIGER_POSITION). */
    @Column(length = 60)
    private String param;

    @Column(length = 400)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ActionStatus status = ActionStatus.PROPOSED;

    @Column(length = 120)
    private String proposedTo;

    private Instant proposedAt;

    @Column(length = 120)
    private String decidedBy;

    private Instant decidedAt;
}
