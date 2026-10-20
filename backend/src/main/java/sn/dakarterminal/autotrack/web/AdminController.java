package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.AdminService;
import sn.dakarterminal.autotrack.service.AuditService;
import sn.dakarterminal.autotrack.service.PrivilegeService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;

/**
 * Administration : comptes du personnel et journal d'audit.
 * Comptes : administrateur uniquement. Journal : administrateur et superviseur (lecture).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final AuditService audit;
    private final PrivilegeService privileges;

    public AdminController(AdminService admin, AuditService audit, PrivilegeService privileges) {
        this.admin = admin;
        this.audit = audit;
        this.privileges = privileges;
    }

    // ------------------------------------------------------------------ Privilèges des profils

    @GetMapping("/privileges")
    @PreAuthorize("hasAnyAuthority('PAGE_PROFILS','PRIVILEGES_GERER')")
    public PrivilegeMatrix privileges() {
        return privileges.matrix();
    }

    /** Matrice complète {profil: [privilèges]} ; seules les différences sont enregistrées et journalisées. */
    @PutMapping("/privileges")
    @PreAuthorize("hasAuthority('PRIVILEGES_GERER')")
    public PrivilegeMatrix savePrivileges(@Valid @RequestBody PrivilegeUpdate r, @AuthenticationPrincipal AuthPrincipal me) {
        return privileges.save(r.grants(), me.fullName());
    }

    @PostMapping("/privileges/reset")
    @PreAuthorize("hasAuthority('PRIVILEGES_GERER')")
    public PrivilegeMatrix resetPrivileges() {
        return privileges.resetDefaults();
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public AdminOverview overview() {
        return admin.overview();
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public List<AdminUserDto> users() {
        return admin.list();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public UserWithPassword create(@Valid @RequestBody UserCreateRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return admin.create(r, me);
    }

    @PutMapping("/users/{username}")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public AdminUserDto update(@PathVariable String username, @Valid @RequestBody UserUpdateRequest r,
                               @AuthenticationPrincipal AuthPrincipal me) {
        return admin.update(username, r, me);
    }

    @PostMapping("/users/{username}/disable")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public AdminUserDto disable(@PathVariable String username, @AuthenticationPrincipal AuthPrincipal me) {
        return admin.setEnabled(username, false, me);
    }

    @PostMapping("/users/{username}/enable")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public AdminUserDto enable(@PathVariable String username, @AuthenticationPrincipal AuthPrincipal me) {
        return admin.setEnabled(username, true, me);
    }

    @PostMapping("/users/{username}/unlock")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public AdminUserDto unlock(@PathVariable String username) {
        return admin.unlock(username);
    }

    @PostMapping("/users/{username}/reset-password")
    @PreAuthorize("hasAuthority('UTILISATEURS_GERER')")
    public UserWithPassword resetPassword(@PathVariable String username) {
        return admin.resetPassword(username);
    }

    /** Journal d'audit filtrable : category, action, actor, q (texte libre), days, limit. */
    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('PAGE_AUDIT')")
    public List<AuditDto> audit(@RequestParam(required = false) String category,
                                @RequestParam(required = false) String action,
                                @RequestParam(required = false) String actor,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "7") int days,
                                @RequestParam(defaultValue = "500") int limit) {
        return audit.search(category, action, actor, q, days, limit);
    }
}
