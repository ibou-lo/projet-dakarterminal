package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.dakarterminal.autotrack.domain.ParkZone;

import java.util.List;
import java.util.Optional;

public interface ParkZoneRepository extends JpaRepository<ParkZone, Long> {

    Optional<ParkZone> findByCode(String code);

    List<ParkZone> findAllByOrderByCodeAsc();
}
