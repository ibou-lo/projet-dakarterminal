package sn.dakarterminal.autotrack.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Envoi de SMS / e-mails. Dans le prototype, les messages sont journalisés et
 * conservés en mémoire (visibles via /api/notifications) : à brancher sur la
 * passerelle SMS du terminal pour le pilote.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Notification(Instant at, String channel, String to, String message) {
    }

    private final List<Notification> sent = Collections.synchronizedList(new ArrayList<>());

    public void sms(String to, String message) {
        send("SMS", to, message);
    }

    public void email(String to, String message) {
        send("EMAIL", to, message);
    }

    private void send(String channel, String to, String message) {
        log.info("[{}] → {} : {}", channel, to, message);
        sent.add(0, new Notification(Instant.now(), channel, to, message));
        if (sent.size() > 100) sent.remove(sent.size() - 1);
    }

    public List<Notification> recent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }
}
