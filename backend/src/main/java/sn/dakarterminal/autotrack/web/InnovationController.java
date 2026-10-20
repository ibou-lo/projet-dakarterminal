package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.InnovationService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

import java.util.List;

/** Étiquettes hybrides QR + RFID, étiquettes de places et armoire à clés. */
@RestController
@RequestMapping("/api")
public class InnovationController {

    private final InnovationService innovations;

    public InnovationController(InnovationService innovations) {
        this.innovations = innovations;
    }

    @GetMapping("/tags")
    public List<TagDto> tags(@RequestParam(required = false) String zone, @RequestParam(required = false) String q) {
        return innovations.tags(zone, q);
    }

    /** Associe l'étiquette RFID (EPC) au passeport ; reusable = étiquette suspendue récupérée au portail. */
    @PostMapping("/vehicles/{vin}/tag")
    @PreAuthorize("hasAuthority('ETIQUETTE_ASSOCIER')")
    public TagDto associate(@PathVariable String vin, @Valid @RequestBody TagRequest r) {
        return innovations.associateTag(vin, r.epc(), r.reusable());
    }

    @GetMapping("/labels/places")
    public List<PlaceLabel> places(@RequestParam(defaultValue = "B") String zone, @RequestParam(required = false) Integer allee) {
        return innovations.placeLabels(zone, allee);
    }

    /** Armoire à clés : state = ARMOIRE | SORTIE | REMISE | INCONNU. */
    @GetMapping("/keys")
    public List<KeyStatus> keys(@RequestParam(required = false) String state) {
        return innovations.keys(state);
    }

    /** vin accepte aussi le contenu du QR de la clé « KEY:<VIN> ». */
    @PostMapping("/keys/{vin}")
    @PreAuthorize("hasAuthority('CLE_MOUVEMENTER')")
    public KeyStatus keyAction(@PathVariable String vin, @Valid @RequestBody KeyActionRequest r,
                               @AuthenticationPrincipal AuthPrincipal me) {
        return innovations.keyAction(vin, r.action(), r.holder(), r.slot(), me.fullName());
    }
}
