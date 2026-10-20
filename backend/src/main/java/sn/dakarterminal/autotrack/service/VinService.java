package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import sn.dakarterminal.autotrack.repository.VehicleRepository;
import sn.dakarterminal.autotrack.web.dto.Dtos.VinCheckResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Contrôle du VIN lu par OCR sur le téléphone (lecture assistée).
 * Le serveur normalise le texte, vérifie le format ISO 3779 et signale les
 * caractères ambigus (S/5, B/8, Z/2, G/6…) que l'agent doit confirmer.
 */
@Service
public class VinService {

    private static final String ALLOWED = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final Map<Character, Integer> TRANSLIT = Map.ofEntries(
            Map.entry('A', 1), Map.entry('B', 2), Map.entry('C', 3), Map.entry('D', 4), Map.entry('E', 5),
            Map.entry('F', 6), Map.entry('G', 7), Map.entry('H', 8), Map.entry('J', 1), Map.entry('K', 2),
            Map.entry('L', 3), Map.entry('M', 4), Map.entry('N', 5), Map.entry('P', 7), Map.entry('R', 9),
            Map.entry('S', 2), Map.entry('T', 3), Map.entry('U', 4), Map.entry('V', 5), Map.entry('W', 6),
            Map.entry('X', 7), Map.entry('Y', 8), Map.entry('Z', 9));
    private static final int[] WEIGHTS = {8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2};
    /** Paires que l'OCR confond fréquemment. */
    private static final String AMBIGUOUS = "S5B8Z2G6";

    private final VehicleRepository vehicles;

    public VinService(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    public VinCheckResponse check(String rawText) {
        List<String> messages = new ArrayList<>();
        String cleaned = normalize(rawText);
        boolean format = cleaned.length() == 17 && cleaned.chars().allMatch(c -> ALLOWED.indexOf(c) >= 0);
        if (cleaned.length() != 17) {
            messages.add("Le VIN doit contenir 17 caractères (" + cleaned.length() + " lus).");
        }
        List<Integer> uncertain = new ArrayList<>();
        for (int i = 0; i < cleaned.length(); i++) {
            if (AMBIGUOUS.indexOf(cleaned.charAt(i)) >= 0 && i >= 10) {
                // Les caractères du numéro de série (positions 11 à 17) sont les plus sensibles.
                uncertain.add(i + 1);
            }
        }
        boolean checkOk = format && checkDigitMatches(cleaned);
        if (format && !checkOk) {
            messages.add("Clé de contrôle (position 9) non conforme : normal pour de nombreux constructeurs hors Amérique du Nord, à vérifier visuellement.");
        }
        if (!uncertain.isEmpty()) {
            messages.add("Caractère(s) incertain(s) en position " + uncertain + " : vérifiez-les sur le véhicule.");
        }
        boolean known = format && vehicles.findByVin(cleaned).isPresent();
        if (known) messages.add("Ce VIN a déjà un passeport numérique.");
        return new VinCheckResponse(cleaned, format, checkOk, uncertain, messages, known);
    }

    /** Majuscules, suppression des espaces, correction I→1, O→0, Q→0 (lettres interdites dans un VIN). */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        s = s.replace('I', '1').replace('O', '0').replace('Q', '0');
        if (s.length() > 17) {
            // L'OCR renvoie parfois du texte autour : on garde la première séquence valide de 17 caractères.
            for (int i = 0; i + 17 <= s.length(); i++) {
                String candidate = s.substring(i, i + 17);
                if (candidate.chars().allMatch(c -> ALLOWED.indexOf(c) >= 0)) return candidate;
            }
        }
        return s;
    }

    static boolean checkDigitMatches(String vin) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = vin.charAt(i);
            int v = Character.isDigit(c) ? c - '0' : TRANSLIT.getOrDefault(c, 0);
            sum += v * WEIGHTS[i];
        }
        int r = sum % 11;
        char expected = r == 10 ? 'X' : (char) ('0' + r);
        return vin.charAt(8) == expected;
    }
}
