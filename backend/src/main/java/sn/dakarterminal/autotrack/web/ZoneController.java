package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.ZoneService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;

/** Zones du plan du parc. Lecture pour tout le personnel, modification réservée au superviseur. */
@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zones;

    public ZoneController(ZoneService zones) {
        this.zones = zones;
    }

    @GetMapping
    public List<ZoneInfo> list() {
        return zones.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ZONE_GERER')")
    public ZoneInfo create(@Valid @RequestBody ZoneCreateRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return zones.create(r, me.fullName());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('ZONE_GERER')")
    public ZoneInfo update(@PathVariable String code, @Valid @RequestBody ZoneUpdateRequest r) {
        return zones.update(code, r);
    }

    @PostMapping("/{code}/allees")
    @PreAuthorize("hasAuthority('ZONE_GERER')")
    public ZoneInfo addAllees(@PathVariable String code, @Valid @RequestBody AlleeAddRequest r) {
        return zones.addAllees(code, r);
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAuthority('ZONE_GERER')")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        zones.delete(code);
        return ResponseEntity.noContent().build();
    }
}
