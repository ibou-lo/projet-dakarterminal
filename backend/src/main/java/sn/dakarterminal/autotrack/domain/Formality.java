package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.FormalityStatus;
import sn.dakarterminal.autotrack.domain.Enums.FormalityType;

import java.time.Instant;

/** Une formalité d'enlèvement (BL, BAE, paiement…) et son état. */
@Entity
@Table(name = "formality", uniqueConstraints = @UniqueConstraint(columnNames = {"vehicle_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class Formality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormalityType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormalityStatus status = FormalityStatus.PENDING;

    /** Référence du document (n° BAE, n° de quittance…). */
    @Column(length = 60)
    private String reference;

    /** Origine : « Plateforme de formalités », « Caisse », « Saisie manuelle »… */
    @Column(length = 60)
    private String source;

    private Instant receivedAt;

    public Formality(Vehicle vehicle, FormalityType type) {
        this.vehicle = vehicle;
        this.type = type;
    }

    public boolean isReceived() {
        return status == FormalityStatus.RECEIVED;
    }
}
