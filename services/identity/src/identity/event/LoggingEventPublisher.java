package identity.event;

import java.time.Instant;
import java.util.UUID;

import analytics.record.ActivityRecorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class LoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    private final ActivityRecorder activities;

    public LoggingEventPublisher(ActivityRecorder activities) {
        this.activities = activities;
    }

    @Override
    public void publishLoggedIn(String userId) {
        deliver(envelope(userId));
    }

    @Override
    public void publishGroupCreated(String groupId, String ownerId) {
        deliver(groupEnvelope(groupId, ownerId));
    }

    private void deliver(String envelope) {
        log.info(envelope);
        activities.record(envelope);
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

    static String groupEnvelope(String groupId, String ownerId) {
        return "{"
            + "\"eventId\":\"" + UUID.randomUUID() + "\","
            + "\"type\":\"identity.group.created\","
            + "\"occurredAt\":\"" + Instant.now() + "\","
            + "\"producer\":\"identity\","
            + "\"payload\":{\"groupId\":\"" + groupId + "\",\"ownerId\":\"" + ownerId + "\"}"
            + "}";
    }
}