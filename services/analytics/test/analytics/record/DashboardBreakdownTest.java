package analytics.record;

import analytics.query.ConversationTotal;
import analytics.query.Dashboard;
import analytics.query.DashboardQuery;
import analytics.query.DashboardService;
import analytics.query.DateRange;
import analytics.query.DayTotal;
import analytics.query.DirectGroupSplit;
import analytics.query.FriendActivity;
import analytics.query.GroupTotal;
import analytics.query.HeatmapCell;
import analytics.query.MediaDayTotal;
import analytics.query.MediaTextSplit;
import analytics.query.MessageDayTotal;
import analytics.query.UserActivity;
import analytics.store.ActivityBreakdown;
import analytics.store.ActivityNote;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private static final String BINH = "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff";
    private static final String GROUP = "11111111-2222-4333-8444-555555555555";
    private static final String DIRECT = "99999999-8888-4777-8666-555555555555";

    @Test
    void countsDaysUsersGroupsAndRecipients() {
        MemoryBreakdown stored = new MemoryBreakdown();
        AggregateActivityRecorder recorder = new AggregateActivityRecorder(stored, stored);
        recorder.record(envelope("login-1", ActivityTypes.LOGGED_IN, "{\"userId\":\"" + ANH + "\"}"));
        recorder.record(envelope(
            "group-1",
            ActivityTypes.GROUP_CREATED,
            "{\"groupId\":\"" + GROUP + "\",\"ownerId\":\"" + ANH + "\",\"memberCount\":2}"
        ));
        recorder.record(envelope(
            "message-1",
            ActivityTypes.MESSAGE_CREATED,
            "{\"senderId\":\"" + ANH + "\",\"conversationId\":\"" + GROUP
                + "\",\"conversationType\":\"GROUP\",\"recipientIds\":[\"" + BINH + "\"],\"type\":\"TEXT\"}"
        ));
        recorder.record(envelope(
            "message-2",
            ActivityTypes.MESSAGE_CREATED,
            "{\"senderId\":\"" + ANH + "\",\"conversationId\":\"" + DIRECT
                + "\",\"conversationType\":\"DIRECT\",\"recipientIds\":[\"" + BINH + "\"],\"type\":\"IMAGE\"}"
        ));

        Dashboard dashboard = new DashboardService(stored).current();
        assertEquals(2, dashboard.getActiveUsers());
        assertEquals(1, dashboard.getMessagesByDay().size());
        assertEquals(2, dashboard.getMessagesByDay().get(0).getTotal());
        assertEquals(1, dashboard.getTopGroups().size());
        assertEquals(GROUP, dashboard.getTopGroups().get(0).getGroupId());

        UserActivity mine = new DashboardService(stored).mine(ANH);
        assertEquals(1, mine.getLogins());
        assertEquals(2, mine.getMessagesSent());
        assertEquals(0, mine.getMessagesReceived());
        assertEquals(1, mine.getGroupsCreated());
        assertEquals(1, mine.getDirectVsGroup().getDirect());
        assertEquals(1, mine.getDirectVsGroup().getGroup());
        assertEquals(1, mine.getMediaVsText().getText());
        assertEquals(1, mine.getMediaVsText().getMedia());

        UserActivity peer = new DashboardService(stored).mine(BINH);
        assertEquals(2, peer.getMessagesReceived());
        assertEquals(0, peer.getMessagesSent());
    }

    @Test
    void rangeFiltersUserDayCounters() {
        MemoryBreakdown stored = new MemoryBreakdown();
        AggregateActivityRecorder recorder = new AggregateActivityRecorder(stored, stored);
        recorder.record(envelopeAt(
            "msg-old",
            ActivityTypes.MESSAGE_CREATED,
            "2026-10-01T12:00:00Z",
            "{\"senderId\":\"" + ANH + "\",\"conversationId\":\"" + DIRECT
                + "\",\"conversationType\":\"DIRECT\",\"recipientIds\":[\"" + BINH + "\"],\"type\":\"TEXT\"}"
        ));
        recorder.record(envelopeAt(
            "msg-new",
            ActivityTypes.MESSAGE_CREATED,
            "2026-10-25T12:00:00Z",
            "{\"senderId\":\"" + ANH + "\",\"conversationId\":\"" + DIRECT
                + "\",\"conversationType\":\"DIRECT\",\"recipientIds\":[\"" + BINH + "\"],\"type\":\"TEXT\"}"
        ));

        UserActivity filtered = new DashboardService(stored).mine(
            ANH,
            DateRange.parse("2026-10-20", "2026-10-31")
        );
        assertEquals(1, filtered.getMessagesSent());
        assertEquals(1, filtered.getMessagesByDay().size());
        assertEquals("2026-10-25", filtered.getMessagesByDay().get(0).getDay());
    }

    @Test
    void missingSessionIsRejected() {
        assertThrows(DashboardService.MissingSession.class, () -> new DashboardService(new MemoryBreakdown()).mine(" "));
    }

    private static String envelope(String eventId, String type, String payload) {
        return envelopeAt(eventId, type, "2026-10-25T00:00:00Z", payload);
    }

    private static String envelopeAt(String eventId, String type, String occurredAt, String payload) {
        return "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"type\":\"" + type + "\","
            + "\"occurredAt\":\"" + occurredAt + "\","
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
        private final Map<String, long[]> lifetime = new HashMap<>();
        private final Map<String, Map<LocalDate, long[]>> byDay = new HashMap<>();
        private final Map<String, Long> friends = new HashMap<>();
        private final Map<LocalDate, Set<String>> activeByDay = new HashMap<>();

        @Override
        public boolean recordIfNew(String eventId, String type, Instant occurredAt) {
            return ids.add(eventId);
        }

        @Override
        public long count(String type) {
            return 0;
        }

        @Override
        public void note(ActivityNote note) {
            LocalDate day = note.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate();
            if (ActivityTypes.LOGGED_IN.equals(note.type())) {
                users.add(note.actorId());
                activeByDay.computeIfAbsent(day, ignored -> new HashSet<>()).add(note.actorId());
                bumpLifetime(note.actorId(), 0, 1);
                bumpDay(note.actorId(), day, 0, 1);
            } else if (ActivityTypes.GROUP_CREATED.equals(note.type())) {
                groups.add(note.subjectId());
                bumpLifetime(note.actorId(), 3, 1);
                bumpDay(note.actorId(), day, 3, 1);
            } else if (ActivityTypes.MESSAGE_CREATED.equals(note.type())) {
                days.merge(day.toString(), 1L, Long::sum);
                users.add(note.actorId());
                activeByDay.computeIfAbsent(day, ignored -> new HashSet<>()).add(note.actorId());
                bumpLifetime(note.actorId(), 1, 1);
                bumpDay(note.actorId(), day, 1, 1);
                boolean direct = "DIRECT".equalsIgnoreCase(note.conversationType());
                bumpDay(note.actorId(), day, direct ? 4 : 5, 1);
                boolean media = ActivityTypes.isMediaMessage(note.contentType());
                bumpDay(note.actorId(), day, media ? 7 : 6, 1);
                if (groups.contains(note.subjectId()) || "GROUP".equalsIgnoreCase(note.conversationType())) {
                    groupTotals.merge(note.subjectId(), 1L, Long::sum);
                }
                for (String recipient : note.recipientIds()) {
                    users.add(recipient);
                    activeByDay.computeIfAbsent(day, ignored -> new HashSet<>()).add(recipient);
                    bumpLifetime(recipient, 2, 1);
                    bumpDay(recipient, day, 2, 1);
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
        public UserActivity activityOf(String userId, DateRange range) {
            long[] sums = new long[8];
            List<MessageDayTotal> series = new ArrayList<>();
            Map<LocalDate, long[]> daysForUser = byDay.getOrDefault(userId, Map.of());
            daysForUser.forEach((day, row) -> {
                if (!range.contains(day)) {
                    return;
                }
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += row[i];
                }
                series.add(new MessageDayTotal(day.toString(), row[1], row[2]));
            });
            series.sort((a, b) -> a.getDay().compareTo(b.getDay()));
            return new UserActivity(
                sums[0],
                sums[1],
                sums[2],
                sums[3],
                sums[1] > 0 ? 1 : 0,
                series,
                new MediaTextSplit(sums[6], sums[7]),
                new DirectGroupSplit(sums[4], sums[5]),
                List.<HeatmapCell>of(),
                new FriendActivity(friends.getOrDefault(userId, 0L), 0, 0, 0),
                List.<ConversationTotal>of(),
                range
            );
        }

        @Override
        public long distinctActive(LocalDate from, LocalDate to) {
            Set<String> ids = new HashSet<>();
            activeByDay.forEach((day, users) -> {
                if ((from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to))) {
                    ids.addAll(users);
                }
            });
            return ids.size();
        }

        @Override
        public List<DayTotal> registrationsByDay(DateRange range) {
            return List.of();
        }

        @Override
        public long sumDayTotal(List<DayTotal> days) {
            return days.stream().mapToLong(DayTotal::getTotal).sum();
        }

        @Override
        public long verifications(DateRange range) {
            return 0;
        }

        @Override
        public long messengersInRange(DateRange range) {
            return users.size();
        }

        @Override
        public long activeInRange(DateRange range) {
            return distinctActive(range.from(), range.to());
        }

        @Override
        public long friendRequests(DateRange range) {
            return 0;
        }

        @Override
        public long friendAccepted(DateRange range) {
            return 0;
        }

        @Override
        public long groupsCreated(DateRange range) {
            return groups.size();
        }

        @Override
        public double avgMembersPerGroup(DateRange range) {
            return groups.isEmpty() ? 0 : 2;
        }

        @Override
        public List<MediaDayTotal> mediaByDay(DateRange range) {
            return List.of();
        }

        private void bumpLifetime(String userId, int slot, long amount) {
            long[] row = lifetime.computeIfAbsent(userId, ignored -> new long[4]);
            row[slot] += amount;
        }

        private void bumpDay(String userId, LocalDate day, int slot, long amount) {
            Map<LocalDate, long[]> daysForUser = byDay.computeIfAbsent(userId, ignored -> new HashMap<>());
            long[] row = daysForUser.computeIfAbsent(day, ignored -> new long[8]);
            row[slot] += amount;
        }
    }
}
