package identity.event;

import analytics.query.ActivityStats;
import analytics.query.StatsService;
import analytics.record.AggregateActivityRecorder;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        assertTrue(envelope.contains("\"memberCount\":1"));
        assertFalse(envelope.contains("name"));
    }

    @Test
    void memberEnvelopesCarryTheContract() {
        String group = "11111111-2222-4333-8444-555555555555";
        String alice = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        String bob = "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee";

        String added = LoggingEventPublisher.memberAddedEnvelope(group, alice, bob);
        String removed = LoggingEventPublisher.memberRemovedEnvelope(group, alice, bob);
        String left = LoggingEventPublisher.memberLeftEnvelope(group, bob);
        String transferred = LoggingEventPublisher.ownerTransferredEnvelope(group, alice, bob);
        String dissolved = LoggingEventPublisher.dissolvedEnvelope(group, alice);

        assertTrue(added.contains("\"type\":\"identity.group.member_added\""));
        assertTrue(added.contains("\"payload\":{\"groupId\":\"" + group + "\",\"actorId\":\"" + alice + "\",\"userId\":\"" + bob + "\"}"));
        assertTrue(removed.contains("\"type\":\"identity.group.member_removed\""));
        assertTrue(removed.contains("\"actorId\":\"" + alice + "\",\"userId\":\"" + bob + "\""));
        assertTrue(left.contains("\"type\":\"identity.group.member_left\""));
        assertTrue(left.contains("\"payload\":{\"groupId\":\"" + group + "\",\"userId\":\"" + bob + "\"}"));
        assertTrue(transferred.contains("\"type\":\"identity.group.owner_transferred\""));
        assertTrue(transferred.contains("\"fromUserId\":\"" + alice + "\",\"toUserId\":\"" + bob + "\""));
        assertTrue(dissolved.contains("\"type\":\"identity.group.dissolved\""));
        assertTrue(dissolved.contains("\"payload\":{\"groupId\":\"" + group + "\",\"actorId\":\"" + alice + "\"}"));
        for (String envelope : List.of(added, removed, left, transferred, dissolved)) {
            assertTrue(envelope.contains("\"producer\":\"identity\""));
            assertTrue(envelope.contains("\"eventId\":\""));
            assertTrue(envelope.contains("\"occurredAt\":\""));
        }
    }

    @Test
    void everyMembershipChangeReachesTheListenersAndAFailingListenerStopsNobody() {
        String group = "11111111-2222-4333-8444-555555555555";
        String alice = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        String bob = "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        List<GroupMembershipChange> heard = new ArrayList<>();
        GroupMembershipListener failing = change -> {
            throw new IllegalStateException("boom");
        };
        LoggingEventPublisher publisher = new LoggingEventPublisher(
            envelope -> { }, List.of(failing, heard::add));

        publisher.publishMemberAdded(group, alice, bob);
        publisher.publishMemberRemoved(group, alice, bob);
        publisher.publishMemberLeft(group, bob);
        publisher.publishOwnerTransferred(group, alice, bob);
        publisher.publishGroupDissolved(group, alice);

        assertEquals(List.of(
            new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_ADDED, group, alice, bob),
            new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_REMOVED, group, alice, bob),
            new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_LEFT, group, bob, bob),
            new GroupMembershipChange(GroupMembershipChange.Type.OWNER_TRANSFERRED, group, alice, bob),
            new GroupMembershipChange(GroupMembershipChange.Type.GROUP_DISSOLVED, group, alice, null)), heard);
    }

    @Test
    void membershipEnvelopesAreRecordedButDoNotChangeTheCounters() {
        MemoryActivities stored = new MemoryActivities();
        LoggingEventPublisher publisher = new LoggingEventPublisher(new AggregateActivityRecorder(stored));

        publisher.publishMemberAdded("11111111-2222-4333-8444-555555555555", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        ActivityStats stats = new StatsService(stored).current();
        assertEquals(0, stats.getLogins());
        assertEquals(0, stats.getGroupsCreated());
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
