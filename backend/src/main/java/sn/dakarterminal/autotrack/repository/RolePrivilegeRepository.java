package sn.dakarterminal.autotrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.dakarterminal.autotrack.domain.RolePrivilege;

import java.util.Optional;

public interface RolePrivilegeRepository extends JpaRepository<RolePrivilege, Long> {

    Optional<RolePrivilege> findByRoleAndPrivilege(String role, String privilege);

    Optional<RolePrivilege> findTopByOrderByUpdatedAtDesc();
}
