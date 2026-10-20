package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.dakarterminal.autotrack.domain.Enums.ReadSource;

import java.time.Instant;

/** Une place du parc, identifiée par un QR code collé au sol ou sur un poteau. */
@Entity
@Table(name = "parking_place", indexes = @Index(columnList = "zone,allee"))
@Getter
@Setter
@NoArgsConstructor
public class ParkingPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code lisible : « B-3-18 ». */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 5)
    private String zone;

    private int allee;

    private int numero;

    /** Contenu du QR code de la place : « PL:B-3-18 ». */
    @Column(nullable = false, unique = true, length = 40)
    private String qrPayload;

    /** Dernière lecture connue de la place (pour la carte « fraîcheur »). */
    private Instant lastReadAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReadSource lastReadSource;

    public ParkingPlace(String zone, int allee, int numero) {
        this.zone = zone;
        this.allee = allee;
        this.numero = numero;
        this.code = zone + "-" + allee + "-" + numero;
        this.qrPayload = "PL:" + this.code;
    }

    /** Libellé affiché : « B · 3 · 18 ». */
    public String label() {
        return zone + " · " + allee + " · " + numero;
    }
}
