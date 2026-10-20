package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParkingPlaceRepository extends JpaRepository<ParkingPlace, Long> {

    Optional<ParkingPlace> findByCode(String code);

    Optional<ParkingPlace> findByQrPayload(String qrPayload);

    List<ParkingPlace> findByZoneOrderByAlleeAscNumeroAsc(String zone);

    List<ParkingPlace> findAllByOrderByZoneAscAlleeAscNumeroAsc();

    long countByZone(String zone);

    @Query("select coalesce(max(p.allee), 0) from ParkingPlace p where p.zone = :zone")
    int maxAllee(@Param("zone") String zone);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from ParkingPlace p where p.zone = :zone")
    int deleteByZone(@Param("zone") String zone);
}
