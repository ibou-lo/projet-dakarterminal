package sn.dakarterminal.autotrack.assistant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.AssistantAction;
import sn.dakarterminal.autotrack.domain.Enums.ActionStatus;
import sn.dakarterminal.autotrack.repository.AssistantActionRepository;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.MovementService;
import sn.dakarterminal.autotrack.service.PickupService;
import sn.dakarterminal.autotrack.service.VehicleService;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.time.Instant;

/**
 * Point d'entrée de l'assistant : délègue la réponse au moteur, puis exécute
 * une action uniquement après confirmation explicite d'un humain.
 */
@Service
@Transactional
public class AssistantService {

    private final AssistantEngine engine;
    private final AssistantActionRepository actions;
    private final PickupService pickup;
    private final VehicleService vehicles;
    private final MovementService movements;

    public AssistantService(AssistantEngine engine, AssistantActionRepository actions, PickupService pickup,
                            VehicleService vehicles, MovementService movements) {
        this.engine = engine;
        this.actions = actions;
        this.pickup = pickup;
        this.vehicles = vehicles;
        this.movements = movements;
    }

    public ChatReply chat(ChatRequest req, AuthPrincipal user) {
        return engine.reply(req, user);
    }

    public ActionProposal confirm(Long id, boolean accept, AuthPrincipal user) {
        AssistantAction a = actions.findById(id).orElseThrow(() -> ApiException.notFound("Action"));
        if (a.getStatus() != ActionStatus.PROPOSED) {
            throw ApiException.conflict("Action déjà traitée");
        }
        if (accept) {
            switch (a.getType()) {
                case "ALERTE_BAE" -> pickup.createBaeAlert(a.getVin(), "AUTO_TICKET".equals(a.getParam()), user.fullName());
                case "RELANCE_TRANSITAIRE" -> pickup.remindForwarder(a.getVin(), user.fullName());
                case "CORRIGER_POSITION" -> vehicles.park(a.getVin(), a.getParam(), user.fullName());
                case "TACHE_VERIFICATION" -> movements.createTask(new TaskRequest(a.getVin(), a.getParam(),
                        a.getDescription(), null), user.fullName());
                default -> throw ApiException.badRequest("Type d'action inconnu : " + a.getType());
            }
        }
        a.setStatus(accept ? ActionStatus.CONFIRMED : ActionStatus.CANCELLED);
        a.setDecidedBy(user.fullName());
        a.setDecidedAt(Instant.now());
        return new ActionProposal(a.getId(), a.getType(), a.getVin(), a.getDescription(), a.getStatus().name());
    }
}
