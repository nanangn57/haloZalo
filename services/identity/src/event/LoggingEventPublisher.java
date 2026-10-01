package event;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class LoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publishLoggedIn(String userId) {
        log.info(envelope(userId));
    }

    static String envelope(String userId) {
        return "{"
            + "\"eventId\":\"" + UUID.randomUUID() + "\","
            + "\"type\":\"identity.user.logged_in\","
            + "\"occurredAt\":\"" + Instant.now() + "\","
            + "\"producer\":\"identity\","
            + "\"payload\":{\"userId\":\"" + userId + "\"}"
            + "}";
    }
}