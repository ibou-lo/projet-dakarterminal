package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.AuditEvent.Category;
import sn.dakarterminal.autotrack.domain.Enums.Role;
import sn.dakarterminal.autotrack.domain.RolePrivilege;
import sn.dakarterminal.autotrack.repository.RolePrivilegeRepository;
import sn.dakarterminal.autotrack.security.Privilege;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.PrivilegeDef;
import sn.dakarterminal.autotrack.web.dto.Dtos.PrivilegeMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Privilèges effectifs de chaque profil = valeurs par défaut du catalogue + réglages de l'administration.
 * Gardés en cache (lus à chaque requête par le filtre JWT) et rechargés à chaque modification.
 * Une modification s'applique dès la requête suivante des utilisateurs concernés.
 */
@Service
public class PrivilegeService {

    private static final List<Role> ROLES = List.of(Role.ADMIN, Role.SUPERVISEUR, Role.AGENT, Role.PORTAIL);

    private final RolePrivilegeRepository repo;
    private final AuditService audit;
    private final Map<Role, Set<Privilege>> cache = new ConcurrentHashMap<>();

    public PrivilegeService(RolePrivilegeRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** Privilèges effectifs d'un profil (vide pour le client). */
    public Set<Privilege> of(Role role) {
        if (!Privilege.CONFIGURABLE_ROLES.contains(role)) return Set.of();
        return cache.computeIfAbsent(role, this::load);
    }

    public List<String> names(Role role) {
        return of(role).stream().map(Enum::name).sorted().toList();
    }

    private Set<Privilege> load(Role role) {
        Map<String, Boolean> overrides = new HashMap<>();
        for (RolePrivilege rp : repo.findAll()) {
            if (rp.getRole().equals(role.name())) overrides.put(rp.getPrivilege(), rp.isGranted());
        }
        EnumSet<Privilege> set = EnumSet.noneOf(Privilege.class);
        for (Privilege p : Privilege.values()) {
            boolean granted = p.locked().contains(role) || overrides.getOrDefault(p.name(), p.defaults().contains(role));
            if (granted) set.add(p);
        }
        return Collections.unmodifiableSet(set);
    }

    @Transactional(readOnly = true)
    public PrivilegeMatrix matrix() {
        List<PrivilegeDef> defs = Arrays.stream(Privilege.values())
                .map(p -> new PrivilegeDef(p.name(), p.kind().name(), p.group(), p.label(), p.description(),
                        p.defaults().stream().map(Enum::name).toList(), p.locked().stream().map(Enum::name).toList()))
                .toList();
        Map<String, List<String>> grants = new LinkedHashMap<>();
        for (Role r : ROLES) grants.put(r.name(), names(r));
        var last = repo.findTopByOrderByUpdatedAtDesc();
        return new PrivilegeMatrix(ROLES.stream().map(Enum::name).toList(), defs, grants,
                last.map(RolePrivilege::getUpdatedAt).orElse(null), last.map(RolePrivilege::getUpdatedBy).orElse(null),
                repo.count());
    }

    /**
     * Enregistre la matrice complète envoyée par l'écran d'administration.
     * Seules les différences avec l'état actuel sont écrites et journalisées (une ligne d'audit par profil).
     */
    @Transactional
    public PrivilegeMatrix save(Map<String, List<String>> grants, String actor) {
        for (Role role : ROLES) {
            List<String> wanted = grants.get(role.name());
            if (wanted == null) continue;
            Set<Privilege> target = EnumSet.noneOf(Privilege.class);
            for (String code : wanted) {
                try {
                    target.add(Privilege.valueOf(code));
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("Privilège inconnu : " + code);
                }
            }
            for (Privilege p : Privilege.values()) {
                if (p.locked().contains(role) && !target.contains(p)) {
                    throw ApiException.badRequest("Le privilège « " + p.label() + " » ne peut pas être retiré au profil " + role.name());
                }
            }
            Set<Privilege> current = of(role);
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            for (Privilege p : Privilege.values()) {
                boolean now = current.contains(p);
                boolean next = target.contains(p);
                if (now == next) continue;
                RolePrivilege rp = repo.findByRoleAndPrivilege(role.name(), p.name())
                        .orElseGet(() -> new RolePrivilege(role.name(), p.name(), next, actor));
                rp.setGranted(next);
                rp.setUpdatedBy(actor);
                rp.setUpdatedAt(java.time.Instant.now());
                repo.save(rp);
                (next ? added : removed).add(p.label());
            }
            if (!added.isEmpty() || !removed.isEmpty()) {
                String details = (added.isEmpty() ? "" : "Ajoutés : " + String.join(", ", added))
                        + (added.isEmpty() || removed.isEmpty() ? "" : " · ")
                        + (removed.isEmpty() ? "" : "Retirés : " + String.join(", ", removed));
                audit.log(Category.ADMINISTRATION, "PRIVILEGES_MODIFIES", "Privilèges du profil modifiés", role.name(), details, true);
            }
            cache.remove(role);
        }
        return matrix();
    }

    /** Revient aux droits par défaut du catalogue pour tous les profils. */
    @Transactional
    public PrivilegeMatrix resetDefaults() {
        long n = repo.count();
        repo.deleteAllInBatch();
        cache.clear();
        audit.log(Category.ADMINISTRATION, "PRIVILEGES_REINITIALISES", "Privilèges remis par défaut", "Tous les profils",
                n + " réglage(s) supprimé(s)", true);
        return matrix();
    }
}
