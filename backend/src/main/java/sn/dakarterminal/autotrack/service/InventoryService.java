package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.ParkingPlace;
import sn.dakarterminal.autotrack.domain.ReadEvent;
import sn.dakarterminal.autotrack.domain.Vehicle;
import sn.dakarterminal.autotrack.domain.Enums.ReadSource;
import sn.dakarterminal.autotrack.repository.ParkingPlaceRepository;
import sn.dakarterminal.autotrack.repository.ReadEventRepository;
import sn.dakarterminal.autotrack.repository.VehicleRepository;
import sn.dakarterminal.autotrack.web.ApiException;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inventaire : rafale de QR lus en filmant une allée, lectures des portiques
 * RFID d'entrée de zone, suggestion de place libre.
 */
@Service
@Transactional
public class InventoryService {

    private final ParkingPlaceRepository places;
    private final VehicleRepository vehicles;
    private final ReadEventRepository reads;

    public InventoryService(ParkingPlaceRepository places, VehicleRepository vehicles, ReadEventRepository reads) {
        this.places = places;
        this.vehicles = vehicles;
        this.reads = reads;
    }

    /**
     * Compare les lectures d'une allée avec les fiches. Les écarts ne corrigent
     * rien automatiquement : l'agent confirme chaque correction.
     */
    public SweepResult sweep(SweepRequest r, String agent) {
        List<ParkingPlace> allee = places.findByZoneOrderByAlleeAscNumeroAsc(r.zone()).stream()
                .filter(p -> p.getAllee() == r.allee()).toList();
        if (allee.isEmpty()) throw ApiException.notFound("Allée " + r.zone() + "·" + r.allee());
        Map<String, Vehicle> expected = new HashMap<>();
        for (Vehicle v : vehicles.findAllOnPark()) {
            if (v.getPlace() != null && v.getPlace().getZone().equals(r.zone()) && v.getPlace().getAllee() == r.allee()) {
                expected.put(v.getPlace().getCode(), v);
            }
        }
        Map<String, String> read = new LinkedHashMap<>();
        if (r.reads() != null) {
            for (SweepRead sr : r.reads()) {
                if (sr.placeCode() != null) read.put(sr.placeCode(), sr.vin() == null || sr.vin().isBlank() ? null : sr.vin());
            }
        }
        Instant now = Instant.now();
        int conform = 0, empty = 0;
        List<SweepGap> gaps = new ArrayList<>();
        for (var e : read.entrySet()) {
            String code = e.getKey();
            String vin = e.getValue();
            Vehicle exp = expected.get(code);
            places.findByCode(code).ifPresent(p -> {
                p.setLastReadAt(now);
                p.setLastReadSource(ReadSource.RAFALE);
            });
            reads.save(new ReadEvent(code, r.zone(), vin, ReadSource.RAFALE, agent, now));
            if (vin == null && exp == null) {
                empty++;
            } else if (vin != null && exp != null && exp.getVin().equals(vin)) {
                conform++;
            } else if (vin == null) {
                gaps.add(new SweepGap("PLACE_VIDE", code, exp.getVin(), code,
                        "Place lue vide alors que " + exp.getShortCode() + " y est enregistré"));
            } else {
                Optional<Vehicle> actual = vehicles.findByVin(vin);
                String expectedPlace = actual.map(v -> v.getPlace() == null ? null : v.getPlace().getCode()).orElse(null);
                gaps.add(new SweepGap("VEHICULE_INATTENDU", code, vin, expectedPlace,
                        "…" + vin.substring(Math.max(0, vin.length() - 12)) + " lu en " + VehicleService.label(code)
                                + (expectedPlace == null ? " (sans fiche)" : ", attendu en " + VehicleService.label(expectedPlace))));
            }
        }
        List<String> notRead = allee.stream().map(ParkingPlace::getCode).filter(c -> !read.containsKey(c)).toList();
        return new SweepResult(read.size(), allee.size(), conform, empty, gaps, notRead);
    }

    /** Lecture d'un portique RFID d'entrée de zone (localisation au niveau de la zone). */
    public void zoneRead(String epcOrVin, String zone, String reader) {
        Vehicle v = vehicles.findByAnyCode(epcOrVin)
                .orElseThrow(() -> ApiException.notFound("Étiquette " + epcOrVin));
        reads.save(new ReadEvent(null, zone, v.getVin(), ReadSource.RFID_PORTIQUE, reader, Instant.now()));
    }

    /** Place libre suggérée dans une zone (la plus proche de l'entrée, allée par allée). */
    @Transactional(readOnly = true)
    public PlaceSuggestion suggest(String zone) {
        Set<String> occupied = vehicles.findAllOnPark().stream()
                .filter(v -> v.getPlace() != null).map(v -> v.getPlace().getCode()).collect(Collectors.toSet());
        List<ParkingPlace> free = places.findByZoneOrderByAlleeAscNumeroAsc(zone).stream()
                .filter(p -> !occupied.contains(p.getCode())).toList();
        if (free.isEmpty()) throw ApiException.conflict("Aucune place libre en zone " + zone);
        // Privilégie l'allée 3 puis les allées voisines (proches de la voie de circulation), place la plus basse.
        ParkingPlace best = free.stream()
                .min(Comparator.comparingInt((ParkingPlace p) -> Math.abs(p.getAllee() - 3)).thenComparing(p -> -p.getNumero()))
                .orElse(free.get(0));
        return new PlaceSuggestion(best.getCode(), best.label(), best.getQrPayload(), free.size());
    }
}
