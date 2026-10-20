package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GatePassageRepository extends JpaRepository<GatePassage, Long> {

    List<GatePassage> findTop20ByOrderByReadAtDesc();

    long countByDecisionAndReadAtAfter(GateDecision decision, Instant since);

    List<GatePassage> findByVinAndDecision(String vin, GateDecision decision);
}
