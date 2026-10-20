package sn.dakarterminal.autotrack.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.AuditEvent;

import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("select a from AuditEvent a where a.at >= :since order by a.at desc")
    List<AuditEvent> findSince(@Param("since") Instant since, Pageable page);

    long countByActionAndAtAfter(String action, Instant after);

    List<AuditEvent> findTop8ByActorOrderByAtDesc(String actor);

    long deleteByAtBefore(Instant limit);
}
