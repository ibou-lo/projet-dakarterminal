package sn.dakarterminal.autotrack.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.repository.AuditEventRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Durée de conservation du journal d'audit (minimisation des données personnelles — loi sénégalaise
 * n° 2008-12, RGPD) : les événements plus anciens que la durée fixée sont supprimés chaque nuit.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditEventRepository events;
    private final int retentionDays;

    public AuditRetentionJob(AuditEventRepository events, @Value("${autotrack.audit.retention-days:365}") int retentionDays) {
        this.events = events;
        this.retentionDays = Math.max(30, retentionDays);
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void purge() {
        long n = events.deleteByAtBefore(Instant.now().minus(Duration.ofDays(retentionDays)));
        if (n > 0) log.info("Journal d'audit : {} événement(s) de plus de {} jours supprimé(s)", n, retentionDays);
    }
}
