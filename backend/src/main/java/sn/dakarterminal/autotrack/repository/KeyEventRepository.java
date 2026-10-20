package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.dakarterminal.autotrack.domain.KeyEvent;

import java.util.List;
import java.util.Optional;

public interface KeyEventRepository extends JpaRepository<KeyEvent, Long> {

    Optional<KeyEvent> findFirstByVinOrderByAtDesc(String vin);

    List<KeyEvent> findByVinOrderByAtAsc(String vin);

    List<KeyEvent> findAllByOrderByAtDesc();
}
