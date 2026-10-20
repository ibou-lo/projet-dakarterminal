package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.ReadSource;

import java.time.Instant;

/**
 * Une lecture physique : scan QR d'une place, passage sous un portique RFID,
 * inventaire en rafale. Alimente la chaîne de garde et la carte « fraîcheur ».
 */
@Entity
@Table(name = "read_event", indexes = {@Index(columnList = "placeCode"), @Index(columnList = "vin")})
@Getter
@Setter
@NoArgsConstructor
public class ReadEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Place lue (null pour un portique d'entrée de zone). */
    @Column(length = 20)
    private String placeCode;

    /** Zone du lecteur (portiques) ou de la place. */
    @Column(length = 10)
    private String zone;

    @Column(length = 17)
    private String vin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReadSource source;

    @Column(length = 120)
    private String reader;

    private Instant readAt;

    public ReadEvent(String placeCode, String zone, String vin, ReadSource source, String reader, Instant readAt) {
        this.placeCode = placeCode;
        this.zone = zone;
        this.vin = vin;
        this.source = source;
        this.reader = reader;
        this.readAt = readAt;
    }
}
