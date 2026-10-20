package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.KeyEvent;
import sn.dakarterminal.autotrack.domain.ParkingPlace;
import sn.dakarterminal.autotrack.domain.Vehicle;
import sn.dakarterminal.autotrack.repository.KeyEventRepository;
import sn.dakarterminal.autotrack.repository.ParkingPlaceRepository;
import sn.dakarterminal.autotrack.repository.VehicleRepository;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.*;

/**
 * Innovations QR & RFID :
 * - étiquette hybride (QR « VH:<VIN> » + puce RFID) associée au passeport, variante réutilisable ;
 * - étiquettes imprimables des véhicules et des places ;
 * - armoire à clés (QR « KEY:<VIN> ») : qui détient la clé, pour quel véhicule.
 */
@Service
@Transactional
public class InnovationService {

    private final VehicleService vehicleService;
    private final VehicleRepository vehicles;
    private final ParkingPlaceRepository places;
    private final KeyEventRepository keys;

    public InnovationService(VehicleService vehicleService, VehicleRepository vehicles, ParkingPlaceRepository places,
                             KeyEventRepository keys) {
        this.vehicleService = vehicleService;
        this.vehicles = vehicles;
        this.places = places;
        this.keys = keys;
    }

    // ------------------------------------------------------------ Étiquettes

    /** Associe (ou remplace) l'étiquette RFID d'un véhicule. Une puce ne peut servir qu'à un véhicule sur le parc. */
    public TagDto associateTag(String vinOrCode, String epc, boolean reusable) {
        Vehicle v = vehicleService.require(vinOrCode);
        String e = epc.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        boolean vinLike = e.length() == 17 && !e.contains("-");
        if (!e.matches("[0-9A-Z][0-9A-Z-]{5,39}") || vinLike || e.startsWith("DAT-")) {
            throw ApiException.badRequest("« " + epc.trim() + " » n'est pas un code de puce RFID (ex. E280-218456) : lisez la puce, pas le QR du véhicule");
        }
        if (e.equals(v.getVin()) || e.equals(v.getShortCode())) {
            throw ApiException.badRequest("Lisez le code de la puce RFID, pas le VIN ni le code DAT");
        }
        vehicles.findByRfidEpc(e).filter(o -> !o.getId().equals(v.getId()) && o.getExitedAt() == null)
                .ifPresent(o -> {
                    throw ApiException.conflict("L'étiquette " + e + " est déjà associée à " + o.getShortCode());
                });
        v.setRfidEpc(e);
        v.setTagReusable(reusable);
        return tag(v);
    }

    @Transactional(readOnly = true)
    public List<TagDto> tags(String zone, String q) {
        String query = q == null ? "" : sn.dakarterminal.autotrack.repository.VehicleRepository.cleanCode(q);
        return vehicles.findAllOnPark().stream()
                .filter(v -> zone == null || zone.isBlank() || (v.getPlace() != null && zone.equals(v.getPlace().getZone())))
                .filter(v -> query.isEmpty() || v.getVin().contains(query) || v.getShortCode().contains(query)
                        || query.equals(v.getRfidEpc()))
                .sorted(Comparator.comparing((Vehicle v) -> v.getPlace() == null ? "~" : v.getPlace().getCode()))
                .limit(200)
                .map(InnovationService::tag)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlaceLabel> placeLabels(String zone, Integer allee) {
        return places.findByZoneOrderByAlleeAscNumeroAsc(zone).stream()
                .filter(p -> allee == null || p.getAllee() == allee)
                .map(p -> new PlaceLabel(p.getCode(), p.label(), p.getQrPayload()))
                .toList();
    }

    static TagDto tag(Vehicle v) {
        ParkingPlace p = v.getPlace();
        return new TagDto(v.getVin(), v.getShortCode(), v.getModel(), p == null ? "Non affecté" : p.label(),
                v.getRfidEpc(), v.isTagReusable(), "VH:" + v.getVin(), "KEY:" + v.getVin());
    }

    // ------------------------------------------------------------ Armoire à clés

    public KeyStatus keyAction(String vinOrCode, String action, String holder, String slot, String actor) {
        Vehicle v = vehicleService.require(vinOrCode);   // accepte KEY:<VIN>, VH:<VIN>, DAT-…, EPC
        KeyEvent.Action a;
        try {
            a = KeyEvent.Action.valueOf(action);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Action attendue : DEPOSEE, SORTIE ou REMISE_CLIENT");
        }
        String who = a == KeyEvent.Action.DEPOSEE ? "Armoire" : (holder == null || holder.isBlank() ? actor : holder);
        String where = a == KeyEvent.Action.DEPOSEE
                ? (slot == null || slot.isBlank() ? defaultSlot(v) : slot) : null;
        keys.save(new KeyEvent(v.getVin(), a, who, where, actor));
        return status(v);
    }

    @Transactional(readOnly = true)
    public List<KeyStatus> keys(String state) {
        Map<String, KeyEvent> last = new HashMap<>();
        for (KeyEvent k : keys.findAllByOrderByAtDesc()) last.putIfAbsent(k.getVin(), k);
        return vehicles.findAllOnPark().stream()
                .map(v -> status(v, last.get(v.getVin())))
                .filter(s -> state == null || state.isBlank() || s.state().equals(state))
                .sorted(Comparator.comparing(KeyStatus::state).thenComparing(KeyStatus::vin))
                .limit(300)
                .toList();
    }

    private KeyStatus status(Vehicle v) {
        return status(v, keys.findFirstByVinOrderByAtDesc(v.getVin()).orElse(null));
    }

    private KeyStatus status(Vehicle v, KeyEvent k) {
        String place = v.getPlace() == null ? "Non affecté" : v.getPlace().label();
        if (k == null) {
            return new KeyStatus(v.getVin(), v.getShortCode(), v.getModel(), place, "INCONNU", null, null, null, "KEY:" + v.getVin());
        }
        String state = switch (k.getAction()) {
            case DEPOSEE -> "ARMOIRE";
            case SORTIE -> "SORTIE";
            case REMISE_CLIENT -> "REMISE";
        };
        return new KeyStatus(v.getVin(), v.getShortCode(), v.getModel(), place, state, k.getHolder(), k.getSlot(), k.getAt(),
                "KEY:" + v.getVin());
    }

    private static String defaultSlot(Vehicle v) {
        return v.getPlace() == null ? "Armoire réception" : "Armoire " + v.getPlace().getZone() + " · crochet " + v.getPlace().getNumero();
    }
}
