package identity.event;

import analytics.query.ActivityStats;
import analytics.query.StatsService;
import analytics.record.AggregateActivityRecorder;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingEventPublisherTest {
    @Test
    void envelopeCarriesLoginContract() {
        String envelope = LoggingEventPublisher.envelope("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        assertTrue(envelope.contains("\"type\":\"identity.user.logged_in\""));
        assertTrue(envelope.contains("\"producer\":\"identity\""));
        assertTrue(envelope.contains("\"payload\":{\"userId\":\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\"}"));
        assertFalse(envelope.contains("password"));
        assertFalse(envelope.contains("email"));
    }

    @Test
    void eachEnvelopeHasItsOwnEventId() {
        String first = LoggingEventPublisher.envelope("user-1");
        String second = LoggingEventPublisher.envelope("user-1");

        assertNotEquals(eventId(first), eventId(second));
    }

    @Test
    void groupEnvelopeCarriesTheContract() {
        String envelope = LoggingEventPublisher.groupEnvelope(
            "11111111-2222-4333-8444-555555555555",
            "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        );

        assertTrue(envelope.contains("\"type\":\"identity.group.created\""));
        assertTrue(envelope.contains("\"producer\":\"identity\""));
        assertTrue(envelope.contains("\"groupId\":\"11111111-2222-4333-8444-555555555555\""));
        assertTrue(envelope.contains("\"ownerId\":\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\""));
        assertFalse(envelope.contains("name"));
    }

    @Test
    void publishedEnvelopesAreCounted() {
        MemoryActivities stored = new MemoryActivities();
        LoggingEventPublisher publisher = new LoggingEventPublisher(new AggregateActivityRecorder(stored));

        publisher.publishLoggedIn("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        publisher.publishGroupCreated("11111111-2222-4333-8444-555555555555", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        publisher.publishLoggedIn("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        ActivityStats stats = new StatsService(stored).current();
        assertEquals(2, stats.getLogins());
        assertEquals(1, stats.getGroupsCreated());
        assertEquals(0, stats.getMessagesCreated());
    }

    private static String eventId(String envelope) {
        int start = envelope.indexOf("\"eventId\":\"") + "\"eventId\":\"".length();
        return envelope.substring(start, envelope.indexOf('"', start));
    }

    private static final class MemoryActivities implements ActivityRepository {
        private final Set<String> ids = new HashSet<>();
        private final Map<String, Long> totals = new HashMap<>();

        @Override
        public boolean recordIfNew(String eventId, String type, Instant occurredAt) {
            if (!ids.add(eventId)) {
                return false;
            }
            totals.merge(type, 1L, Long::sum);
            return true;
        }

        @Override
        public long count(String type) {
            return totals.getOrDefault(type, 0L);
        }
    }
}
