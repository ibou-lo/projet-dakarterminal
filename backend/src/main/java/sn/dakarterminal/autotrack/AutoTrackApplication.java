package sn.dakarterminal.autotrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Dakar AutoTrack : le passeport numérique des véhicules au terminal.
 * Prototype : données fictives, H2 en mémoire par défaut.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AutoTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoTrackApplication.class, args);
    }
}
