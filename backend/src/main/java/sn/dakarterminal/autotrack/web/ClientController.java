package sn.dakarterminal.autotrack.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.ClientService;
import sn.dakarterminal.autotrack.web.dto.Dtos.ClientStatus;

import java.util.Map;

/**
 * Espace client (rôle CLIENT). Le BL vient du JWT : un client ne peut consulter
 * que son propre véhicule.
 */
@RestController
@RequestMapping("/api/client")
public class ClientController {

    private final ClientService clients;

    public ClientController(ClientService clients) {
        this.clients = clients;
    }

    @GetMapping("/me")
    public ClientStatus me(@AuthenticationPrincipal AuthPrincipal me) {
        return clients.status(me.bl());
    }

    @PostMapping("/notify")
    public ClientStatus notifyMe(@RequestBody Map<String, Boolean> body, @AuthenticationPrincipal AuthPrincipal me) {
        return clients.notifyMe(me.bl(), !Boolean.FALSE.equals(body.get("enabled")));
    }
}
