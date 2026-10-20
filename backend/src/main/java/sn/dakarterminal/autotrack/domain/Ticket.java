package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Ticket de retrait remis au client. Le QR contient un jeton signé (HMAC),
 * à durée limitée et à usage unique.
 */
@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String ticketNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vehicle vehicle;

    /** Contenu du QR code : « TKT.<numero>.<expiration>.<signature> ». */
    @Column(nullable = false, length = 200)
    private String qrToken;

    private Instant issuedAt;

    private Instant validUntil;

    /** Créneau proposé au client. */
    @Column(length = 40)
    private String slot;

    private boolean used;

    private Instant usedAt;

    @Column(length = 120)
    private String issuedBy;
}
