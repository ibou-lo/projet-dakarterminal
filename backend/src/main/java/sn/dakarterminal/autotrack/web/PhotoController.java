package sn.dakarterminal.autotrack.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sn.dakarterminal.autotrack.service.VehicleService;
import sn.dakarterminal.autotrack.web.dto.Dtos.PhotoDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Photos de l'état à la réception. Prototype : stockage sur disque local
 * (dossier autotrack.storage.photos-dir). Pour le pilote : stockage objet (S3/MinIO).
 */
@RestController
@RequestMapping("/api/vehicles/{vin}/photos")
public class PhotoController {

    private static final Set<String> VIEWS = Set.of("Avant", "Arrière", "Gauche", "Droite", "Toit", "Intérieur",
            "Compteur", "Plaque VIN", "Dommage");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final VehicleService vehicles;
    private final Path root;

    public PhotoController(VehicleService vehicles,
                           @Value("${autotrack.storage.photos-dir:./data/photos}") String dir) {
        this.vehicles = vehicles;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('VEHICULE_RECEPTIONNER')")
    public PhotoDto upload(@PathVariable String vin, @RequestParam String view, @RequestParam MultipartFile file)
            throws IOException {
        String realVin = vehicles.require(vin).getVin();
        if (!VIEWS.contains(view)) throw ApiException.badRequest("Vue inconnue : " + view);
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw ApiException.badRequest("Photo vide ou trop lourde (8 Mo max)");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!type.equals("image/jpeg") && !type.equals("image/png")) throw ApiException.badRequest("Formats acceptés : JPEG, PNG");
        String ext = type.equals("image/png") ? ".png" : ".jpg";
        String name = view.replaceAll("[^A-Za-z]", "") + "-" + System.currentTimeMillis() + ext;
        Path dir = root.resolve(realVin).normalize();
        if (!dir.startsWith(root)) throw ApiException.badRequest("Chemin invalide");
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(name));
        return new PhotoDto(view, realVin + "/" + name);
    }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> get(@PathVariable String vin, @PathVariable String name) {
        String realVin = vehicles.require(vin).getVin();
        Path file = root.resolve(realVin).resolve(name).normalize();
        if (!file.startsWith(root.resolve(realVin)) || !Files.exists(file)) throw ApiException.notFound("Photo");
        MediaType mt = name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(mt).body(new PathResource(file));
    }
}
