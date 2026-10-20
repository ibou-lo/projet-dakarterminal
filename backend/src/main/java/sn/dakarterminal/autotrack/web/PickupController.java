package sn.dakarterminal.autotrack.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.domain.Enums.FormalityType;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.PickupService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;
import java.util.Map;

/** Formalités, tickets de retrait et tableau des enlèvements. */
@RestController
@RequestMapping("/api")
public class PickupController {

    private final PickupService pickup;

    public PickupController(PickupService pickup) {
        this.pickup = pickup;
    }

    /**
     * Réception d'une formalité (ex. BAE). Dans le pilote, cet appel viendra de
     * l'intégration avec la plateforme de formalités ; ici, il permet de la simuler.
     */
    @PostMapping("/vehicles/{vin}/formalities/{type}/receive")
    @PreAuthorize("hasAuthority('FORMALITE_SIMULER')")
    public Passport receive(@PathVariable String vin, @PathVariable FormalityType type,
                            @RequestBody(required = false) FormalityUpdate body,
                            @AuthenticationPrincipal AuthPrincipal me) {
        return pickup.receiveFormality(vin, type, body, me.fullName());
    }

    @PostMapping("/vehicles/{vin}/formalities/{type}/reset")
    @PreAuthorize("hasAuthority('FORMALITE_SIMULER')")
    public Passport reset(@PathVariable String vin, @PathVariable FormalityType type) {
        return pickup.resetFormality(vin, type);
    }

    @PostMapping("/vehicles/{vin}/ticket")
    @PreAuthorize("hasAuthority('TICKET_EMETTRE')")
    public TicketDto issueTicket(@PathVariable String vin, @RequestBody(required = false) TicketRequest body,
                                 @AuthenticationPrincipal AuthPrincipal me) {
        return pickup.issueTicket(vin, body == null ? null : body.slot(), me.fullName());
    }

    @PostMapping("/vehicles/{vin}/remind")
    @PreAuthorize("hasAuthority('TRANSITAIRE_RELANCER')")
    public Map<String, String> remind(@PathVariable String vin, @AuthenticationPrincipal AuthPrincipal me) {
        pickup.remindForwarder(vin, me.fullName());
        return Map.of("status", "sent");
    }

    /** Liste des tickets de retrait : state=ACTIVE | USED | EXPIRED (vide = tous). */
    @GetMapping("/tickets")
    public List<TicketDto> tickets(@RequestParam(required = false) String state) {
        return pickup.listTickets(state);
    }

    @GetMapping("/tickets/{number}")
    public TicketDto ticket(@PathVariable String number) {
        return pickup.ticket(number);
    }

    @GetMapping("/pickups/board")
    public List<PickupColumn> board() {
        return pickup.board();
    }
}
