package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** État du véhicule enregistré à la réception : photos, réserves, carburant, clés. */
@Entity
@Table(name = "inspection")
@Getter
@Setter
@NoArgsConstructor
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private Vehicle vehicle;

    /** Carburant : « 1/4 », « 1/2 »… */
    @Column(length = 10)
    private String fuelLevel;

    private Integer keysCount;

    /** Kilométrage : null tant qu'il n'est pas saisi. */
    private Integer mileage;

    /** Références des photos (vue → fichier stocké). */
    @ElementCollection
    @CollectionTable(name = "inspection_photo")
    private List<Photo> photos = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "inspection_reserve")
    private List<Reserve> reserves = new ArrayList<>();

    @Column(length = 120)
    private String inspectedBy;

    private Instant inspectedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Photo {
        /** Avant, Arrière, Gauche, Droite, Toit, Intérieur, Compteur, Plaque VIN. */
        @Column(name = "view_name", length = 30)
        private String view;
        @Column(length = 200)
        private String storageRef;

        public Photo(String view, String storageRef) {
            this.view = view;
            this.storageRef = storageRef;
        }
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Reserve {
        @Column(length = 80)
        private String location;
        @Column(length = 200)
        private String description;
        @Column(length = 30)
        private String photoView;

        public Reserve(String location, String description, String photoView) {
            this.location = location;
            this.description = description;
            this.photoView = photoView;
        }
    }
}
