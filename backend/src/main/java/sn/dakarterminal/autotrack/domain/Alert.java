package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Alerte métier : « BAE reçu → prévenir le transitaire », sortie bloquée,
 * incohérence de position…
 */
@Entity
@Table(name = "alert")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** BAE_RECU, SORTIE_BLOQUEE, INCOHERENCE, SCAN_MANQUANT… */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 17)
    private String vin;

    @Column(length = 300)
    private String message;

    /** Pour BAE_RECU : générer automatiquement le ticket au déclenchement. */
    private boolean autoTicket;

    private boolean active = true;

    private boolean triggered;

    private Instant createdAt;

    private Instant triggeredAt;

    @Column(length = 120)
    private String createdBy;

    public Alert(String type, String vin, String message, String createdBy) {
        this.type = type;
        this.vin = vin;
        this.message = message;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
