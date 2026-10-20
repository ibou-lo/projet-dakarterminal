package sn.dakarterminal.autotrack.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Contrôles de configuration au démarrage (OWASP ASVS V14 — configuration sécurisée).
 * Avec AUTOTRACK_PRODUCTION=true, l'application REFUSE de démarrer si :
 * secrets de démonstration, secret de ticket trop court, console H2 active, données de démo activées.
 * Sans ce drapeau (démo, poste de développement), ces points sont signalés en avertissement.
 */
@Component
public class SecurityStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupCheck.class);

    private final AutoTrackProperties props;
    private final boolean production;
    private final boolean h2Console;
    private final boolean apiDocs;

    public SecurityStartupCheck(AutoTrackProperties props,
                                @Value("${autotrack.security.production:false}") boolean production,
                                @Value("${spring.h2.console.enabled:false}") boolean h2Console,
                                @Value("${springdoc.api-docs.enabled:true}") boolean apiDocs) {
        this.props = props;
        this.production = production;
        this.h2Console = h2Console;
        this.apiDocs = apiDocs;
    }

    @PostConstruct
    void check() {
        StringBuilder problems = new StringBuilder();
        if (isDemoSecret(props.security().jwtSecret())) problems.append("\n - secret JWT de démonstration (AUTOTRACK_JWT_SECRET)");
        String ticket = props.security().ticketSecret();
        if (isDemoSecret(ticket)) problems.append("\n - secret de signature des tickets de démonstration (AUTOTRACK_TICKET_SECRET)");
        if (ticket == null || ticket.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.append("\n - secret de signature des tickets trop court (32 caractères minimum)");
        }
        if (h2Console) problems.append("\n - console H2 active");
        if (props.demo().seed()) problems.append("\n - chargement des données de démonstration actif (DEMO_SEED=false)");
        if (problems.isEmpty()) {
            log.info("Contrôle de sécurité de la configuration : OK{}", apiDocs && production ? " (documentation API exposée : API_DOCS=false conseillé)" : "");
            return;
        }
        if (production) {
            throw new IllegalStateException("Configuration refusée en production :" + problems);
        }
        log.warn("CONFIGURATION DE DÉMONSTRATION — à corriger avant toute mise en production :{}", problems);
    }

    private static boolean isDemoSecret(String s) {
        return s == null || s.startsWith("change-me");
    }
}
