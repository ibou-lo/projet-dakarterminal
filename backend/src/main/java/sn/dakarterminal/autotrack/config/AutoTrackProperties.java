package sn.dakarterminal.autotrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Paramètres applicatifs (préfixe « autotrack »). */
@ConfigurationProperties(prefix = "autotrack")
public record AutoTrackProperties(Security security, Demo demo, TicketProps ticket) {

    public record Security(String jwtSecret, long accessTokenMinutes, long refreshTokenHours,
                           String ticketSecret, List<String> allowedOrigins) {
    }

    public record Demo(boolean seed, String clientSmsCode) {
    }

    public record TicketProps(long validityHours) {
    }
}
