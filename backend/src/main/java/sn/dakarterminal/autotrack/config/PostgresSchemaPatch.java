package sn.dakarterminal.autotrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL + ddl-auto=update : Hibernate crée des contraintes CHECK sur les colonnes d'énumération
 * (ex. role IN ('SUPERVISEUR', …)) mais ne les met jamais à jour. Une nouvelle valeur (ex. profil ADMIN)
 * serait alors refusée par une base existante. Ces contraintes sont retirées au démarrage :
 * les valeurs restent contrôlées par l'application (types Java).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresSchemaPatch implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresSchemaPatch.class);

    private final DataSource dataSource;

    public PostgresSchemaPatch(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
        } catch (Exception e) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> checks = jdbc.queryForList("""
                select conrelid::regclass::text as tbl, conname
                from pg_constraint
                where contype = 'c' and connamespace = 'public'::regnamespace and conname like '%\\_check'
                """);
        for (Map<String, Object> ck : checks) {
            String sql = "alter table " + ck.get("tbl") + " drop constraint if exists \"" + ck.get("conname") + "\"";
            try {
                jdbc.execute(sql);
            } catch (Exception e) {
                log.warn("Contrainte non retirée ({}) : {}", ck.get("conname"), e.getMessage());
            }
        }
        if (!checks.isEmpty()) log.info("PostgreSQL : {} contrainte(s) CHECK d'énumération retirée(s)", checks.size());
    }
}
