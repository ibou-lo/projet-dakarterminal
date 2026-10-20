package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.TaskStatus;

import java.time.Instant;

/** Tâche de vérification envoyée à un agent (scan manquant, incohérence, allée à relire). */
@Entity
@Table(name = "verification_task")
@Getter
@Setter
@NoArgsConstructor
public class VerificationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 17)
    private String vin;

    @Column(length = 20)
    private String placeCode;

    @Column(length = 300)
    private String reason;

    @Column(length = 120)
    private String assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskStatus status = TaskStatus.OPEN;

    private Instant createdAt;

    @Column(length = 120)
    private String createdBy;

    public VerificationTask(String vin, String placeCode, String reason, String assignedTo, String createdBy) {
        this.vin = vin;
        this.placeCode = placeCode;
        this.reason = reason;
        this.assignedTo = assignedTo;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
