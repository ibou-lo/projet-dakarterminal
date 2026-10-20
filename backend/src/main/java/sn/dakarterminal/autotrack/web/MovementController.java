package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.MovementService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MovementController {

    private final MovementService movements;

    public MovementController(MovementService movements) {
        this.movements = movements;
    }

    @GetMapping("/movements")
    public List<MovementDto> journal(@RequestParam(defaultValue = "24") int hours) {
        return movements.journal(Math.min(hours, 24 * 30));
    }

    @GetMapping("/movements/stats")
    public MovementStats stats() {
        return movements.stats();
    }

    @GetMapping("/movements/missing-arrivals")
    public List<MovementDto> missing() {
        return movements.missingArrivals();
    }

    @GetMapping("/movements/agents")
    public List<AgentActivity> agents() {
        return movements.agents();
    }

    @GetMapping("/tasks")
    public List<TaskDto> tasks() {
        return movements.openTasks();
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('TACHE_GERER')")
    public TaskDto createTask(@Valid @RequestBody TaskRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return movements.createTask(r, me.fullName());
    }

    @PostMapping("/tasks/{id}/done")
    @PreAuthorize("hasAuthority('TACHE_GERER')")
    public TaskDto close(@PathVariable Long id) {
        return movements.closeTask(id);
    }
}
