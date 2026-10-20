package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.assistant.AssistantService;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.Map;

/** Chat IA (superviseur et agents de terrain). */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return assistant.chat(r, me);
    }

    /** Validation humaine d'une action proposée : {"accept": true|false}. */
    @PostMapping("/actions/{id}")
    @PreAuthorize("hasAuthority('IA_ACTION_VALIDER')")
    public ActionProposal decide(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                 @AuthenticationPrincipal AuthPrincipal me) {
        return assistant.confirm(id, Boolean.TRUE.equals(body.get("accept")), me);
    }
}
