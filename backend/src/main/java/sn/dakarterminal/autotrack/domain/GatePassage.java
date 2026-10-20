package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.GateDecision;

import java.time.Instant;

/** Un passage au portail de sortie (lecture RFID et/ou scan du ticket). */
@Entity
@Table(name = "gate_passage")
@Getter
@Setter
@NoArgsConstructor
public class GatePassage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 17)
    private String vin;

    @Column(length = 30)
    private String ticketNumber;

    private int lane = 1;

    /** Mode de lecture : RFID, QR_TICKET ou RFID+QR. */
    @Column(length = 20)
    private String readMode;

    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GateDecision decision;

    @Column(length = 200)
    private String reason;

    @Column(length = 120)
    private String decidedBy;

    private Instant decidedAt;
}
