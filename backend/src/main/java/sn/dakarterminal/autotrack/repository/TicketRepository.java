package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    @Query("select t from Ticket t join fetch t.vehicle v left join fetch v.place order by t.issuedAt desc")
    List<Ticket> findAllWithVehicle();

    Optional<Ticket> findFirstByVehicleOrderByIssuedAtDesc(Vehicle vehicle);

    long countByUsedFalse();
}
