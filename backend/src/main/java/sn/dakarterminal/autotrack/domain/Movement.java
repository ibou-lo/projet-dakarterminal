package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.MovementType;

import java.time.Instant;

/**
 * Un mouvement du véhicule. La trace est complète quand le départ ET l'arrivée
 * ont été scannés (heure + agent).
 */
@Entity
@Table(name = "movement", indexes = @Index(columnList = "startedAt"))
@Getter
@Setter
@NoArgsConstructor
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(length = 30)
    private String fromPlace;

    @Column(length = 30)
    private String toPlace;

    @Column(length = 120)
    private String agent;

    private Instant startedAt;

    /** Heure du scan d'arrivée ; null = arrivée non scannée (à régulariser). */
    private Instant arrivalScannedAt;

    @Column(length = 200)
    private String note;

    public Movement(Vehicle vehicle, MovementType type, String fromPlace, String toPlace, String agent, Instant startedAt) {
        this.vehicle = vehicle;
        this.type = type;
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.agent = agent;
        this.startedAt = startedAt;
    }

    public boolean isTraceComplete() {
        return arrivalScannedAt != null;
    }
}
