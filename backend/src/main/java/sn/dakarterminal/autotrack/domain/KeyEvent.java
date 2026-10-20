package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mouvement des clés d'un véhicule (armoire à clés). Chaque clé porte le QR
 * « KEY:<VIN> » (ou une puce RFID) : on sait à tout moment qui l'a et pour quel véhicule.
 */
@Entity
@Table(name = "key_event", indexes = @Index(columnList = "vin"))
@Getter
@Setter
@NoArgsConstructor
public class KeyEvent {

    public enum Action { DEPOSEE, SORTIE, REMISE_CLIENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 17)
    private String vin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    /** Personne qui détient la clé après l'action. */
    @Column(length = 120)
    private String holder;

    /** Emplacement dans l'armoire (ex. « Armoire B · crochet 18 »). */
    @Column(length = 60)
    private String slot;

    @Column(length = 120)
    private String recordedBy;

    @Column(name = "event_at", nullable = false)
    private Instant at;

    public KeyEvent(String vin, Action action, String holder, String slot, String recordedBy) {
        this.vin = vin;
        this.action = action;
        this.holder = holder;
        this.slot = slot;
        this.recordedBy = recordedBy;
        this.at = Instant.now();
    }
}
