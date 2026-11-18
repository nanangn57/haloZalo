package analytics.record;

import analytics.query.Dashboard;
import analytics.query.DashboardQuery;
import analytics.query.DashboardService;
import analytics.query.DayTotal;
import analytics.query.GroupTotal;
import analytics.query.UserActivity;
import analytics.store.ActivityBreakdown;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardBreakdownTest {
    private static final String ANH = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String GROUP = "11111111-2222-4333-8444-555555555555";
    private static final String DIRECT = "99999999-8888-4777-8666-555555555555";

    @Test
    void countsDaysUsersAndGroupsWithoutADirectChat() {
        MemoryBreakdown stored = new MemoryBreakdown();
        AggregateActivityRecorder recorder = new AggregateActivityRecorder(stored, stored);
        String login = envelope("login-1", ActivityTypes.LOGGED_IN, "{\"userId\":\"" + ANH + "\"}");
        String group = envelope("group-1", ActivityTypes.GROUP_CREATED, "{\"groupId\":\"" + GROUP + "\",\"ownerId\":\"" + ANH + "\"}");
        String message = envelope(
            "message-1",
            ActivityTypes.MESSAGE_CREATED,
            "{\"senderId\":\"" + ANH + "\",\"conversationId\":\"" + GROUP + "\"}"
        );
        String direct = envelope(
            "message-2",
            ActivityTypes.MESSAGE_CREATED,
            "{\"senderId\":\"" + ANH + "\",\"conversationId\":\"" + DIRECT + "\"}"
        );

        recorder.record(login);
        recorder.record(login);
        recorder.record(group);
        recorder.record(message);
        recorder.record(message);
        recorder.record(direct);

        Dashboard dashboard = new DashboardService(stored).current();
        assertEquals(1, dashboard.getActiveUsers());
        assertEquals(1, dashboard.getMessagesByDay().size());
        assertEquals("2026-10-25", dashboard.getMessagesByDay().get(0).getDay());
        assertEquals(2, dashboard.getMessagesByDay().get(0).getTotal());
        assertEquals(1, dashboard.getTopGroups().size());
        assertEquals(GROUP, dashboard.getTopGroups().get(0).getGroupId());
        assertEquals(1, dashboard.getTopGroups().get(0).getTotal());

        UserActivity mine = new DashboardService(stored).mine(ANH);
        assertEquals(1, mine.getLogins());
        assertEquals(2, mine.getMessagesSent());
        assertEquals(1, mine.getGroupsCreated());
        UserActivity stranger = new DashboardService(stored).mine(DIRECT);
        assertEquals(0, stranger.getLogins());
        assertEquals(0, stranger.getMessagesSent());
        assertEquals(0, stranger.getGroupsCreated());
    }

    @Test
    void missingSessionIsRejected() {
        assertThrows(DashboardService.MissingSession.class, () -> new DashboardService(new MemoryBreakdown()).mine(" "));
    }

    private static String envelope(String eventId, String type, String payload) {
        return "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"type\":\"" + type + "\","
            + "\"occurredAt\":\"2026-10-25T00:00:00Z\","
            + "\"producer\":\"identity\","
            + "\"payload\":" + payload
            + "}";
    }

    private static final class MemoryBreakdown implements ActivityRepository, ActivityBreakdown, DashboardQuery {
        private final Set<String> ids = new HashSet<>();
        private final Set<String> groups = new HashSet<>();
        private final Set<String> users = new HashSet<>();
        private final Map<String, Long> days = new HashMap<>();
        private final Map<String, Long> groupTotals = new HashMap<>();
        private final Map<String, long[]> activity = new HashMap<>();

        @Override
        public boolean recordIfNew(String eventId, String type, Instant occurredAt) {
            return ids.add(eventId);
        }

        @Override
        public long count(String type) {
            return 0;
        }

        @Override
        public void note(String type, Instant occurredAt, String actorId, String subjectId) {
            if (ActivityTypes.LOGGED_IN.equals(type)) {
                users.add(actorId);
                bump(actorId, 0);
            } else if (ActivityTypes.GROUP_CREATED.equals(type)) {
                groups.add(subjectId);
                bump(actorId, 2);
            } else if (ActivityTypes.MESSAGE_CREATED.equals(type)) {
                String day = occurredAt.atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString();
                days.merge(day, 1L, Long::sum);
                users.add(actorId);
                bump(actorId, 1);
                if (groups.contains(subjectId)) {
                    groupTotals.merge(subjectId, 1L, Long::sum);
                }
            }
        }

        @Override
        public List<DayTotal> messagesByDay() {
            List<DayTotal> rows = new ArrayList<>();
            days.forEach((day, total) -> rows.add(new DayTotal(day, total)));
            return rows;
        }

        @Override
        public long activeUsers() {
            return users.size();
        }

        @Override
        public List<GroupTotal> topGroups(int limit) {
            List<GroupTotal> rows = new ArrayList<>();
            groupTotals.forEach((groupId, total) -> rows.add(new GroupTotal(groupId, total)));
            assertTrue(rows.size() <= limit);
            return rows;
        }

        @Override
        public UserActivity activityOf(String userId) {
            long[] row = activity.get(userId);
            if (row == null) {
                return new UserActivity(0, 0, 0);
            }
            return new UserActivity(row[0], row[1], row[2]);
        }

        private void bump(String userId, int slot) {
            long[] row = activity.computeIfAbsent(userId, ignored -> new long[3]);
            row[slot] += 1;
        }
    }
}
