package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.InventoryService;
import sn.dakarterminal.autotrack.service.NotificationService;
import sn.dakarterminal.autotrack.service.ParkQueryService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;
import java.util.Map;

/** Plan du parc, carte « fraîcheur », inventaire en rafale, portiques de zone. */
@RestController
@RequestMapping("/api/park")
public class ParkController {

    private final ParkQueryService query;
    private final InventoryService inventory;
    private final NotificationService notifications;

    public ParkController(ParkQueryService query, InventoryService inventory, NotificationService notifications) {
        this.query = query;
        this.inventory = inventory;
        this.notifications = notifications;
    }

    /** Vue d'ensemble : indicateurs + plan des zones. source=ALL|QR|RFID_PORTIQUE|RAFALE pour la fraîcheur. */
    @GetMapping
    public ParkOverview overview(@RequestParam(required = false) String source) {
        return query.overview(source);
    }

    @GetMapping("/incoherences")
    public List<Incoherence> incoherences() {
        return query.incoherences();
    }

    @GetMapping("/suggest-place")
    public PlaceSuggestion suggest(@RequestParam(defaultValue = "B") String zone) {
        return inventory.suggest(zone);
    }

    @PostMapping("/sweep")
    @PreAuthorize("hasAuthority('INVENTAIRE_RAFALE')")
    public SweepResult sweep(@Valid @RequestBody SweepRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return inventory.sweep(r, me.fullName());
    }

    /** Lecture d'un portique RFID d'entrée de zone : {"epc": "...", "zone": "D"}. */
    @PostMapping("/zone-read")
    public Map<String, String> zoneRead(@RequestBody Map<String, String> body, @AuthenticationPrincipal AuthPrincipal me) {
        inventory.zoneRead(body.get("epc"), body.get("zone"), "Portique zone " + body.get("zone"));
        return Map.of("status", "ok");
    }

    /** SMS / e-mails envoyés (simulés dans le prototype). */
    @GetMapping("/notifications")
    public List<NotificationService.Notification> notifications() {
        return notifications.recent();
    }
}
