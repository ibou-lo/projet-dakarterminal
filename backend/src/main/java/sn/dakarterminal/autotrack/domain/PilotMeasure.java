package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mesure hebdomadaire d'un indicateur du pilote (saisie ou calculée).
 * Dans le prototype, les valeurs sont fictives.
 */
@Entity
@Table(name = "pilot_measure", uniqueConstraints = @UniqueConstraint(columnNames = {"indicator", "week"}))
@Getter
@Setter
@NoArgsConstructor
public class PilotMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SEARCH_TIME_MIN, INVENTORY_RELIABILITY_PCT, PICKUP_PREP_MIN, TRACE_COMPLETE_PCT. */
    @Column(nullable = false, length = 40)
    private String indicator;

    /** Semaine du pilote (0 = référence avant pilote). */
    private int week;

    private double measuredValue;

    public PilotMeasure(String indicator, int week, double measuredValue) {
        this.indicator = indicator;
        this.week = week;
        this.measuredValue = measuredValue;
    }
}
