package sn.dakarterminal.autotrack.security;

import org.springframework.stereotype.Component;
import sn.dakarterminal.autotrack.config.AutoTrackProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Signe le contenu du QR des tickets de retrait :
 * {@code TKT.<numero>.<expiration epoch>.<HMAC-SHA256 base64url>}.
 * Le portail vérifie la signature et l'expiration, même sans réseau, puis
 * l'usage unique côté serveur.
 */
@Component
public class TicketSigner {

    private final byte[] secret;

    public TicketSigner(AutoTrackProperties props) {
        this.secret = props.security().ticketSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String ticketNumber, String vin, Instant validUntil) {
        String payload = ticketNumber + "." + validUntil.getEpochSecond();
        return "TKT." + payload + "." + hmac(payload + "|" + vin);
    }

    /** Résultat de vérification : numéro de ticket si la signature est valide, sinon null. */
    public record Verified(String ticketNumber, Instant validUntil, boolean signatureOk) {
    }

    public Verified verify(String token, String expectedVin) {
        if (token == null || !token.startsWith("TKT.")) return new Verified(null, null, false);
        String[] parts = token.split("\\.");
        if (parts.length != 4) return new Verified(null, null, false);
        String number = parts[1];
        Instant exp;
        try {
            exp = Instant.ofEpochSecond(Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            return new Verified(number, null, false);
        }
        String expected = hmac(number + "." + parts[2] + "|" + expectedVin);
        boolean ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8));
        return new Verified(number, exp, ok);
    }

    /** Extrait le numéro du ticket sans vérifier la signature (pour retrouver le véhicule). */
    public static String numberOf(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        return parts.length >= 2 && parts[0].equals("TKT") ? parts[1] : token.trim();
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
