package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    Optional<Inspection> findByVehicle(Vehicle vehicle);
}
