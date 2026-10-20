package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    List<Movement> findByVehicleOrderByStartedAtAsc(Vehicle vehicle);

    @Query("select m from Movement m join fetch m.vehicle where m.startedAt >= :since order by m.startedAt desc")
    List<Movement> findSince(@Param("since") Instant since);

    @Query("select m from Movement m join fetch m.vehicle where m.arrivalScannedAt is null and m.type in :types order by m.startedAt desc")
    List<Movement> findMissingArrival(@Param("types") List<MovementType> types);
}
