package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByVin(String vin);

    Optional<Vehicle> findByShortCode(String shortCode);

    Optional<Vehicle> findByBlNumber(String blNumber);

    Optional<Vehicle> findByRfidEpc(String rfidEpc);

    /** Véhicules (présents ou sortis) rattachés à une place de la zone. */
    long countByPlaceZone(String zone);

    long countByPlaceZoneAndExitedAtIsNull(String zone);

    List<Vehicle> findByVinEndingWith(String suffix);

    @Query("select v from Vehicle v left join fetch v.place where v.exitedAt is null")
    List<Vehicle> findAllOnPark();

    @Query("select v from Vehicle v left join fetch v.place")
    List<Vehicle> findAllWithPlace();

    @Query("select v from Vehicle v left join fetch v.place where v.place.code = :code and v.exitedAt is null")
    Optional<Vehicle> findOnPlace(@Param("code") String code);

    /** Préfixes des QR imprimés : étiquette véhicule, porte-clé. */
    static String cleanCode(String raw) {
        String k = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (k.startsWith("VH:")) k = k.substring(3);
        else if (k.startsWith("KEY:")) k = k.substring(4);
        return k.replace(" ", "");
    }

    /**
     * Retrouve un véhicule à partir de tout ce qu'un agent peut scanner ou saisir :
     * VIN, QR « VH:<VIN> », QR de clé « KEY:<VIN> », code court « DAT-218456 » ou « 218456 »,
     * EPC de la puce RFID, fin de VIN (≥ 6 caractères, si elle est unique).
     */
    default Optional<Vehicle> findByAnyCode(String raw) {
        String k = cleanCode(raw);
        if (k.isEmpty()) return Optional.empty();
        return findByVin(k)
                .or(() -> findByShortCode(k))
                .or(() -> findByShortCode("DAT-" + k))
                .or(() -> findByRfidEpc(k))
                .or(() -> {
                    if (k.length() < 6 || k.contains(":")) return Optional.empty();
                    List<Vehicle> bySuffix = findByVinEndingWith(k);
                    return bySuffix.size() == 1 ? Optional.of(bySuffix.get(0)) : Optional.empty();
                });
    }
}
