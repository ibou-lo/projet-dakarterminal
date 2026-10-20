package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReadEventRepository extends JpaRepository<ReadEvent, Long> {

    List<ReadEvent> findByVinOrderByReadAtAsc(String vin);

    List<ReadEvent> findTop50BySourceOrderByReadAtDesc(ReadSource source);

    List<ReadEvent> findByReadAtAfterOrderByReadAtDesc(Instant since);
}
