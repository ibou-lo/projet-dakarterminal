package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByVinAndActiveTrue(String vin);

    List<Alert> findByActiveTrueOrderByCreatedAtDesc();

    List<Alert> findByVinAndTypeAndActiveTrueAndTriggeredFalse(String vin, String type);
}
