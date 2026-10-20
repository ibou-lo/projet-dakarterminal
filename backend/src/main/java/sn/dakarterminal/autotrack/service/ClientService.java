package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.Formality;
import sn.dakarterminal.autotrack.domain.Vehicle;
import sn.dakarterminal.autotrack.repository.FormalityRepository;
import sn.dakarterminal.autotrack.repository.TicketRepository;
import sn.dakarterminal.autotrack.repository.VehicleRepository;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.ClientStatus;

import java.util.List;

/**
 * Espace client : le client ne voit que le véhicule de son BL (le BL est porté
 * par son JWT, jamais par un paramètre de requête).
 */
@Service
@Transactional
public class ClientService {

    private final VehicleRepository vehicles;
    private final FormalityRepository formalities;
    private final TicketRepository tickets;
    private final ParkQueryService query;
    private final sn.dakarterminal.autotrack.repository.GatePassageRepository gatePassages;

    public ClientService(VehicleRepository vehicles, FormalityRepository formalities, TicketRepository tickets,
                         ParkQueryService query, sn.dakarterminal.autotrack.repository.GatePassageRepository gatePassages) {
        this.vehicles = vehicles;
        this.formalities = formalities;
        this.tickets = tickets;
        this.query = query;
        this.gatePassages = gatePassages;
    }

    private Vehicle vehicleOf(String bl) {
        return vehicles.findByBlNumber(bl).orElseThrow(() -> ApiException.notFound("Véhicule du BL " + bl));
    }

    @Transactional(readOnly = true)
    public ClientStatus status(String bl) {
        Vehicle v = vehicleOf(bl);
        List<Formality> fs = formalities.findByVehicleOrderByIdAsc(v);
        boolean ready = ParkQueryService.allReceived(fs) && v.getExitedAt() == null;
        String label;
        if (v.getExitedAt() != null) label = "Véhicule sorti du terminal";
        else if (ready) label = "Prêt à enlever";
        else label = "Autorisation de sortie en attente";
        var ticket = tickets.findFirstByVehicleOrderByIssuedAtDesc(v).map(t -> VehicleService.toDto(t, v)).orElse(null);
        return new ClientStatus(v.getShortCode(), v.getModel(), v.getColor(), label, ready,
                fs.stream().filter(f -> !f.isReceived()).map(f -> f.getType().getLabel()).toList(),
                fs.stream().filter(Formality::isReceived).map(f -> f.getType().getLabel()).toList(),
                ticket, v.isClientNotify(), queueAhead(), "Portail véhicules : 7h30 – 18h00, du lundi au samedi");
    }

    /** File d'attente au portail : passages en cours de contrôle (QR d'accueil client). */
    private int queueAhead() {
        return (int) gatePassages.findAll().stream()
                .filter(g -> g.getDecision() == sn.dakarterminal.autotrack.domain.Enums.GateDecision.PENDING_AGENT).count();
    }

    public ClientStatus notifyMe(String bl, boolean on) {
        Vehicle v = vehicleOf(bl);
        v.setClientNotify(on);
        return status(bl);
    }
}
