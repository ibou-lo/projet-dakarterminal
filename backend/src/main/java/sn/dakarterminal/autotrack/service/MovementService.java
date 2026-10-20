package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.Movement;
import sn.dakarterminal.autotrack.domain.VerificationTask;
import sn.dakarterminal.autotrack.domain.Enums.MovementType;
import sn.dakarterminal.autotrack.domain.Enums.TaskStatus;
import sn.dakarterminal.autotrack.repository.MovementRepository;
import sn.dakarterminal.autotrack.repository.VerificationTaskRepository;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Journal des mouvements, indicateurs de traçabilité et tâches de régularisation. */
@Service
@Transactional
public class MovementService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final MovementRepository movements;
    private final VerificationTaskRepository tasks;

    public MovementService(MovementRepository movements, VerificationTaskRepository tasks) {
        this.movements = movements;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<MovementDto> journal(int hours) {
        return movements.findSince(Instant.now().minus(Duration.ofHours(hours))).stream()
                .map(MovementService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementDto> missingArrivals() {
        return movements.findMissingArrival(List.of(MovementType.DEPLACEMENT, MovementType.PRE_SORTIE)).stream()
                .map(MovementService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MovementStats stats() {
        Instant startOfDay = LocalDate.now(DAKAR).atStartOfDay(DAKAR).toInstant();
        List<Movement> today = movements.findSince(startOfDay);
        long complete = today.stream().filter(Movement::isTraceComplete).count();
        long missing = movements.findMissingArrival(List.of(MovementType.DEPLACEMENT, MovementType.PRE_SORTIE)).size();
        OptionalDouble avg = today.stream()
                .filter(m -> m.getType() == MovementType.DEPLACEMENT && m.isTraceComplete())
                .mapToLong(m -> Duration.between(m.getStartedAt(), m.getArrivalScannedAt()).toSeconds())
                .filter(sec -> sec > 0)
                .average();
        Map<String, Long> perHour = new LinkedHashMap<>();
        for (int h = 6; h <= 18; h++) perHour.put(String.format("%02dh", h), 0L);
        today.forEach(m -> {
            int h = m.getStartedAt().atZone(DAKAR).getHour();
            perHour.computeIfPresent(String.format("%02dh", h), (k, v) -> v + 1);
        });
        double pct = today.isEmpty() ? 100.0 : Math.round(complete * 1000.0 / today.size()) / 10.0;
        return new MovementStats(today.size(), pct, missing,
                avg.isPresent() ? Math.round(avg.getAsDouble() / 6.0) / 10.0 : null, perHour);
    }

    @Transactional(readOnly = true)
    public List<AgentActivity> agents() {
        Instant startOfDay = LocalDate.now(DAKAR).atStartOfDay(DAKAR).toInstant();
        return movements.findSince(startOfDay).stream()
                .filter(m -> m.getAgent() != null)
                .collect(Collectors.groupingBy(Movement::getAgent, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new AgentActivity(e.getKey(), e.getValue()))
                .toList();
    }

    public TaskDto createTask(TaskRequest r, String actor) {
        String assignee = r.assignedTo() == null || r.assignedTo().isBlank() ? "Agent le plus proche" : r.assignedTo();
        VerificationTask t = tasks.save(new VerificationTask(r.vin(), r.placeCode(), r.reason(), assignee, actor));
        return toDto(t);
    }

    public TaskDto closeTask(Long id) {
        VerificationTask t = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Tâche"));
        t.setStatus(TaskStatus.DONE);
        return toDto(t);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> openTasks() {
        return tasks.findByStatusOrderByCreatedAtDesc(TaskStatus.OPEN).stream().map(MovementService::toDto).toList();
    }

    public static MovementDto toDto(Movement m) {
        return new MovementDto(m.getId(), m.getStartedAt(), m.getVehicle().getVin(), m.getVehicle().getShortCode(),
                m.getType().name(), m.getFromPlace(), m.getToPlace(), m.getAgent(), m.isTraceComplete(),
                m.getArrivalScannedAt());
    }

    static TaskDto toDto(VerificationTask t) {
        return new TaskDto(t.getId(), t.getVin(), t.getPlaceCode(), t.getReason(), t.getAssignedTo(),
                t.getStatus().name(), t.getCreatedAt());
    }
}
