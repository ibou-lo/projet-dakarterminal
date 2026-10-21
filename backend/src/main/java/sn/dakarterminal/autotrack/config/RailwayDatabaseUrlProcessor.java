package sn.dakarterminal.autotrack.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Railway (et d'autres PaaS) exposent {@code DATABASE_URL=postgresql://user:pass@host:port/db}.
 * Spring attend {@code jdbc:postgresql://...} plus utilisateur / mot de passe séparés.
 * Ne fait rien si {@code DB_URL} est déjà défini (poste local, Docker Compose).
 */
public class RailwayDatabaseUrlProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (hasText(environment.getProperty("DB_URL"))) {
            return;
        }
        String raw = firstText(environment.getProperty("DATABASE_URL"), environment.getProperty("DATABASE_PRIVATE_URL"));
        if (!hasText(raw)) {
            return;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw.startsWith("jdbc:")) {
            map.put("spring.datasource.url", raw);
            environment.getPropertySources().addFirst(new MapPropertySource("railwayDatabaseUrl", map));
            return;
        }
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            return;
        }
        URI uri = URI.create(raw.replaceFirst("^postgres://", "postgresql://"));
        String host = uri.getHost();
        if (host == null) {
            return;
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/autotrack" : uri.getPath();
        String query = uri.getQuery();
        String jdbc = "jdbc:postgresql://" + host + ":" + port + path
                + (query == null || query.isBlank() ? "" : "?" + query);

        map.put("spring.datasource.url", jdbc);
        String userInfo = uri.getUserInfo();
        if (hasText(userInfo)) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            String password = colon < 0 ? "" : userInfo.substring(colon + 1);
            if (!hasText(environment.getProperty("DB_USER"))) {
                map.put("spring.datasource.username", decode(user));
            }
            if (!hasText(environment.getProperty("DB_PASSWORD"))) {
                map.put("spring.datasource.password", decode(password));
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("railwayDatabaseUrl", map));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
