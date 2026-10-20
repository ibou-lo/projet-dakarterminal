package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Zone du parc (A, B, C…) : son libellé et sa disposition. Les places sont créées à partir de la disposition. */
@Entity
@Table(name = "park_zone")
@Getter
@Setter
@NoArgsConstructor
public class ParkZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 5)
    private String code;

    @Column(nullable = false, length = 60)
    private String label;

    /** Usage prévu : VL, utilitaires, pré-sortie… (information). */
    @Column(name = "zone_usage", length = 40)
    private String usage;

    private String createdBy;

    private Instant createdAt;

    public ParkZone(String code, String label, String usage, String createdBy) {
        this.code = code;
        this.label = label;
        this.usage = usage;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
