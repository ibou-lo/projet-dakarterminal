package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Le passeport numérique : un véhicule identifié par son VIN, relié à sa place,
 * à son état à la réception et à ses formalités.
 */
@Entity
@Table(name = "vehicle", indexes = {@Index(columnList = "blNumber"), @Index(columnList = "shortCode")})
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    /** Code court lisible à voix haute : « DAT-218456 ». */
    @Column(nullable = false, unique = true, length = 20)
    private String shortCode;

    /** Identifiant de l'étiquette RFID (EPC), si l'étiquette hybride est activée. */
    @Column(length = 40)
    private String rfidEpc;

    @Column(length = 80)
    private String model;

    @Column(length = 40)
    private String color;

    private Integer modelYear;

    @Column(length = 20)
    private String escale;

    @Column(length = 30)
    private String blNumber;

    /** Bénéficiaire autorisé (affiché masqué côté client). */
    @Column(length = 120)
    private String beneficiaryName;

    /** Fin du numéro de pièce d'identité du bénéficiaire (ex. « 4417 »). */
    @Column(length = 10)
    private String beneficiaryIdSuffix;

    @Column(length = 60)
    private String forwarder;

    @ManyToOne(fetch = FetchType.LAZY)
    private ParkingPlace place;

    /** La position a-t-elle été confirmée par un scan (QR de place, rafale, RFID) ? */
    private boolean positionConfirmed;

    private Instant disembarkedAt;

    private Instant exitedAt;

    /** Code SMS de démonstration pour l'accès client. */
    @Column(length = 10)
    private String clientAccessCode;

    /** Le client a demandé à être prévenu par SMS. */
    private boolean clientNotify;

    /** Étiquette suspendue réutilisable : libérée au portail et réaffectée au véhicule suivant. */
    private boolean tagReusable;

    public Vehicle(String vin, String model, String color, Integer modelYear, String escale, String blNumber) {
        this.vin = vin;
        this.model = model;
        this.color = color;
        this.modelYear = modelYear;
        this.escale = escale;
        this.blNumber = blNumber;
        this.shortCode = "DAT-" + vin.substring(vin.length() - 6);
    }
}
