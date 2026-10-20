package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.GateService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;
import java.util.Map;

/** Portail de sortie (rôles PORTAIL et SUPERVISEUR, cf. SecurityConfig). */
@RestController
@RequestMapping("/api/gate")
public class GateController {

    private final GateService gate;

    public GateController(GateService gate) {
        this.gate = gate;
    }

    /** Lecture RFID du portique et/ou scan du QR du ticket. */
    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('SORTIE_CONTROLER')")
    public GateResult scan(@RequestBody GateScanRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        if ((r.ticketQr() == null || r.ticketQr().isBlank()) && (r.rfidEpc() == null || r.rfidEpc().isBlank())
                && (r.vin() == null || r.vin().isBlank())) {
            throw ApiException.badRequest("Fournir un QR de ticket, une étiquette RFID ou un VIN");
        }
        return gate.scan(r, me.fullName());
    }

    /** Décision de l'agent : EXIT_VALIDATED ou BLOCKED. */
    @PostMapping("/passages/{id}/decision")
    @PreAuthorize("hasAuthority('SORTIE_CONTROLER')")
    public GatePassageDto decide(@PathVariable Long id, @Valid @RequestBody GateDecisionRequest r,
                                 @AuthenticationPrincipal AuthPrincipal me) {
        if (!r.decision().equals("EXIT_VALIDATED") && !r.decision().equals("BLOCKED")) {
            throw ApiException.badRequest("Décision attendue : EXIT_VALIDATED ou BLOCKED");
        }
        return gate.decide(id, r.decision(), r.reason(), me.fullName());
    }

    @GetMapping("/passages")
    public List<GatePassageDto> recent() {
        return gate.recent();
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return gate.todayStats();
    }
}
