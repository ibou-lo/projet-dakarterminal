package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.ParkQueryService;
import sn.dakarterminal.autotrack.service.VehicleService;
import sn.dakarterminal.autotrack.service.VinService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;
import java.util.Map;

/** Passeport numérique : réception, état, stationnement, déplacements. */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicles;
    private final ParkQueryService query;
    private final VinService vinService;

    public VehicleController(VehicleService vehicles, ParkQueryService query, VinService vinService) {
        this.vehicles = vehicles;
        this.query = query;
        this.vinService = vinService;
    }

    /** Inventaire filtrable : status=READY|AWAITING_AUTHORIZATION|POSITION_TO_CONFIRM|EXITED, zone, q. */
    @GetMapping
    public List<VehicleSummary> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String zone,
                                     @RequestParam(required = false) String q) {
        return query.list(status, zone, q);
    }

    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return query.countByStatus();
    }

    @GetMapping("/{vin}")
    public Passport passport(@PathVariable String vin) {
        return vehicles.passport(vin);
    }

    /** Lecture assistée : contrôle d'un VIN lu par OCR sur le téléphone. */
    @PostMapping("/vin-check")
    @PreAuthorize("hasAuthority('VEHICULE_RECEPTIONNER')")
    public VinCheckResponse vinCheck(@Valid @RequestBody VinCheckRequest r) {
        return vinService.check(r.rawText());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VEHICULE_RECEPTIONNER')")
    public Passport receive(@Valid @RequestBody ReceptionRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return vehicles.receive(r, me.fullName());
    }

    @PutMapping("/{vin}/inspection")
    @PreAuthorize("hasAuthority('VEHICULE_RECEPTIONNER')")
    public Passport inspect(@PathVariable String vin, @RequestBody InspectionRequest r,
                            @AuthenticationPrincipal AuthPrincipal me) {
        return vehicles.inspect(vin, r, me.fullName());
    }

    /** Affectation ou déplacement confirmé par le scan du QR de la place. */
    @PostMapping("/{vin}/park")
    @PreAuthorize("hasAuthority('VEHICULE_STATIONNER')")
    public Passport park(@PathVariable String vin, @Valid @RequestBody ParkRequest r,
                         @AuthenticationPrincipal AuthPrincipal me) {
        return vehicles.park(vin, r.placeScan(), me.fullName());
    }

    /** Affectation / réaffectation depuis le poste de supervision. */
    @PostMapping("/{vin}/assign")
    @PreAuthorize("hasAuthority('VEHICULE_STATIONNER')")
    public Passport assign(@PathVariable String vin, @Valid @RequestBody AssignRequest r,
                           @AuthenticationPrincipal AuthPrincipal me) {
        return vehicles.assign(vin, r.placeCode(), r.confirmed(), me.fullName());
    }

    @PostMapping("/{vin}/move")
    @PreAuthorize("hasAuthority('VEHICULE_STATIONNER')")
    public Passport startMove(@PathVariable String vin, @AuthenticationPrincipal AuthPrincipal me) {
        return vehicles.startMove(vin, me.fullName());
    }
}
