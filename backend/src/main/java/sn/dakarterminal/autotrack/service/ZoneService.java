package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.ParkZone;
import sn.dakarterminal.autotrack.domain.ParkingPlace;
import sn.dakarterminal.autotrack.repository.ParkZoneRepository;
import sn.dakarterminal.autotrack.repository.ParkingPlaceRepository;
import sn.dakarterminal.autotrack.repository.VehicleRepository;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.*;

/**
 * Gestion des zones du plan : création d'une zone (allées × places, QR « PL:… » générés),
 * ajout d'allées, renommage, suppression d'une zone jamais utilisée.
 */
@Service
@Transactional
public class ZoneService {

    private static final int MAX_PLACES_PAR_ZONE = 1200;

    private final ParkZoneRepository zones;
    private final ParkingPlaceRepository places;
    private final VehicleRepository vehicles;
    private final NotificationService notifications;

    public ZoneService(ParkZoneRepository zones, ParkingPlaceRepository places, VehicleRepository vehicles,
                       NotificationService notifications) {
        this.zones = zones;
        this.places = places;
        this.vehicles = vehicles;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<ZoneInfo> list() {
        return zones.findAllByOrderByCodeAsc().stream().map(this::info).toList();
    }

    /** Libellés des zones, pour le plan du parc. */
    @Transactional(readOnly = true)
    public Map<String, String> labels() {
        Map<String, String> m = new HashMap<>();
        zones.findAll().forEach(z -> m.put(z.getCode(), z.getLabel()));
        return m;
    }

    public ZoneInfo create(ZoneCreateRequest r, String actor) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (zones.findByCode(code).isPresent() || places.countByZone(code) > 0) {
            throw ApiException.conflict("La zone " + code + " existe déjà");
        }
        if ((long) r.allees() * r.placesParAllee() > MAX_PLACES_PAR_ZONE) {
            throw ApiException.badRequest("Une zone est limitée à " + MAX_PLACES_PAR_ZONE + " places");
        }
        ParkZone z = zones.save(new ParkZone(code, r.label().trim(), blankToNull(r.usage()), actor));
        createAllees(code, 1, r.allees(), r.placesParAllee());
        notifications.sms("Parc", "Nouvelle zone " + code + " « " + z.getLabel() + " » : "
                + r.allees() + " allée(s) × " + r.placesParAllee() + " places. QR des places à imprimer.");
        return info(z);
    }

    public ZoneInfo update(String code, ZoneUpdateRequest r) {
        ParkZone z = require(code);
        z.setLabel(r.label().trim());
        z.setUsage(blankToNull(r.usage()));
        return info(z);
    }

    /** Ajoute des allées à la suite des allées existantes. */
    public ZoneInfo addAllees(String code, AlleeAddRequest r) {
        ParkZone z = require(code);
        long total = places.countByZone(z.getCode()) + (long) r.count() * r.placesParAllee();
        if (total > MAX_PLACES_PAR_ZONE) {
            throw ApiException.badRequest("Une zone est limitée à " + MAX_PLACES_PAR_ZONE + " places");
        }
        int first = places.maxAllee(z.getCode()) + 1;
        createAllees(z.getCode(), first, r.count(), r.placesParAllee());
        return info(z);
    }

    /** Suppression possible uniquement si aucun véhicule n'a jamais été rattaché à la zone (traçabilité). */
    public void delete(String code) {
        ParkZone z = require(code);
        if (vehicles.countByPlaceZone(z.getCode()) > 0) {
            throw ApiException.conflict("Zone " + z.getCode() + " utilisée par des véhicules (présents ou dans l'historique) : suppression impossible");
        }
        places.deleteByZone(z.getCode());
        zones.delete(z);
    }

    private void createAllees(String code, int firstAllee, int count, int placesParAllee) {
        List<ParkingPlace> batch = new ArrayList<>();
        for (int a = firstAllee; a < firstAllee + count; a++) {
            for (int n = 1; n <= placesParAllee; n++) batch.add(new ParkingPlace(code, a, n));
        }
        places.saveAll(batch);
    }

    private ParkZone require(String code) {
        return zones.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Zone " + code));
    }

    private ZoneInfo info(ParkZone z) {
        long total = places.countByZone(z.getCode());
        long occupied = vehicles.countByPlaceZoneAndExitedAtIsNull(z.getCode());
        boolean deletable = vehicles.countByPlaceZone(z.getCode()) == 0;
        return new ZoneInfo(z.getCode(), z.getLabel(), z.getUsage(), places.maxAllee(z.getCode()), total, occupied,
                deletable, z.getCreatedBy(), z.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
