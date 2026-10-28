package analytics.record;

import analytics.query.ActivityStats;
import analytics.query.StatsService;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AggregateActivityRecorderTest {
    @Test
    void recordsLoginGroupAndMessageWithoutCountingARepeat() {
        MemoryActivities stored = new MemoryActivities();
        AggregateActivityRecorder recorder = new AggregateActivityRecorder(stored);
        String message = envelope("same-event", ActivityTypes.MESSAGE_CREATED);

        recorder.record(envelope("login-1", ActivityTypes.LOGGED_IN));
        recorder.record(envelope("group-1", ActivityTypes.GROUP_CREATED));
        recorder.record(message);
        recorder.record(message);
        recorder.record("not-json");
        recorder.record(envelope("other-1", "messaging.media.uploaded"));

        ActivityStats stats = new StatsService(stored).current();
        assertEquals(1, stats.getLogins());
        assertEquals(1, stats.getGroupsCreated());
        assertEquals(1, stats.getMessagesCreated());
    }

    private static String envelope(String eventId, String type) {
        return "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"type\":\"" + type + "\","
            + "\"occurredAt\":\"2026-10-25T00:00:00Z\","
            + "\"producer\":\"messaging\","
            + "\"payload\":{\"messageId\":\"8a1b2c3d-4e5f-6789-abcd-ef0123456789\"}"
            + "}";
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
