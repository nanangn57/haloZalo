package identity.event;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import analytics.record.ActivityRecorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class LoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    private final ActivityRecorder activities;
    private final List<GroupMembershipListener> listeners;

    public LoggingEventPublisher(ActivityRecorder activities) {
        this(activities, List.of());
    }

    public LoggingEventPublisher(ActivityRecorder activities, List<GroupMembershipListener> listeners) {
        this.activities = activities;
        this.listeners = List.copyOf(listeners);
    }

    /**
     * Listeners are other modules in the same process. There may be none, for example when identity runs alone.
     */
    @Autowired
    public LoggingEventPublisher(ActivityRecorder activities, ObjectProvider<GroupMembershipListener> listeners) {
        this(activities, listeners.orderedStream().toList());
    }

    @Override
    public void publishLoggedIn(String userId) {
        deliver(envelope(userId));
    }

    @Override
    public void publishRegistered(String userId) {
        deliver(wrap("identity.user.registered", "{\"userId\":\"" + userId + "\"}"));
    }

    @Override
    public void publishVerified(String userId) {
        deliver(wrap("identity.user.verified", "{\"userId\":\"" + userId + "\"}"));
    }

    @Override
    public void publishGroupCreated(String groupId, String ownerId) {
        publishGroupCreated(groupId, ownerId, 1);
    }

    @Override
    public void publishGroupCreated(String groupId, String ownerId, int memberCount) {
        deliver(groupEnvelope(groupId, ownerId, memberCount));
    }

    @Override
    public void publishFriendRequestSent(String fromUserId, String toUserId) {
        deliver(wrap(
            friendEventId("frs", fromUserId, toUserId),
            "identity.friend.request_sent",
            "{\"fromUserId\":\"" + fromUserId + "\",\"toUserId\":\"" + toUserId + "\"}"
        ));
    }

    @Override
    public void publishFriendRequestAccepted(String fromUserId, String toUserId) {
        deliver(wrap(
            friendEventId("fra", fromUserId, toUserId),
            "identity.friend.request_accepted",
            "{\"fromUserId\":\"" + fromUserId + "\",\"toUserId\":\"" + toUserId + "\"}"
        ));
    }

    @Override
    public void publishMemberAdded(String groupId, String actorId, String memberId) {
        deliver(memberAddedEnvelope(groupId, actorId, memberId));
        notifyListeners(new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_ADDED, groupId, actorId, memberId));
    }

    @Override
    public void publishMemberRemoved(String groupId, String actorId, String memberId) {
        deliver(memberRemovedEnvelope(groupId, actorId, memberId));
        notifyListeners(new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_REMOVED, groupId, actorId, memberId));
    }

    @Override
    public void publishMemberLeft(String groupId, String memberId) {
        deliver(memberLeftEnvelope(groupId, memberId));
        notifyListeners(new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_LEFT, groupId, memberId, memberId));
    }

    @Override
    public void publishOwnerTransferred(String groupId, String fromUserId, String toUserId) {
        deliver(ownerTransferredEnvelope(groupId, fromUserId, toUserId));
        notifyListeners(new GroupMembershipChange(
            GroupMembershipChange.Type.OWNER_TRANSFERRED, groupId, fromUserId, toUserId));
    }

    @Override
    public void publishGroupDissolved(String groupId, String actorId) {
        deliver(dissolvedEnvelope(groupId, actorId));
        notifyListeners(new GroupMembershipChange(GroupMembershipChange.Type.GROUP_DISSOLVED, groupId, actorId, null));
    }

    private void deliver(String envelope) {
        log.info(envelope);
        activities.record(envelope);
    }

    private void notifyListeners(GroupMembershipChange change) {
        for (GroupMembershipListener listener : listeners) {
            try {
                listener.onChange(change);
            } catch (RuntimeException ex) {
                // The change is already stored: a listener that fails must not fail the request.
                log.warn("Group listener failed for {} in group {}", change.type(), change.groupId(), ex);
            }
        }
    }

    static String envelope(String userId) {
        return wrap("identity.user.logged_in", "{\"userId\":\"" + userId + "\"}");
    }

    static String groupEnvelope(String groupId, String ownerId) {
        return groupEnvelope(groupId, ownerId, 1);
    }

    static String groupEnvelope(String groupId, String ownerId, int memberCount) {
        int members = memberCount < 1 ? 1 : memberCount;
        return wrap(
            groupEventId(groupId),
            "identity.group.created",
            "{\"groupId\":\"" + groupId + "\",\"ownerId\":\"" + ownerId + "\",\"memberCount\":" + members + "}"
        );
    }

    static String memberAddedEnvelope(String groupId, String actorId, String memberId) {
        return wrap("identity.group.member_added", "{\"groupId\":\"" + groupId + "\",\"actorId\":\"" + actorId
            + "\",\"userId\":\"" + memberId + "\"}");
    }

    static String memberRemovedEnvelope(String groupId, String actorId, String memberId) {
        return wrap("identity.group.member_removed", "{\"groupId\":\"" + groupId + "\",\"actorId\":\"" + actorId
            + "\",\"userId\":\"" + memberId + "\"}");
    }

    static String memberLeftEnvelope(String groupId, String memberId) {
        return wrap("identity.group.member_left", "{\"groupId\":\"" + groupId + "\",\"userId\":\"" + memberId + "\"}");
    }

    static String ownerTransferredEnvelope(String groupId, String fromUserId, String toUserId) {
        return wrap("identity.group.owner_transferred", "{\"groupId\":\"" + groupId + "\",\"fromUserId\":\""
            + fromUserId + "\",\"toUserId\":\"" + toUserId + "\"}");
    }

    static String dissolvedEnvelope(String groupId, String actorId) {
        return wrap("identity.group.dissolved", "{\"groupId\":\"" + groupId + "\",\"actorId\":\"" + actorId + "\"}");
    }

    private static String wrap(String type, String payload) {
        return wrap(UUID.randomUUID().toString(), type, payload);
    }

    private static String wrap(String eventId, String type, String payload) {
        return "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"type\":\"" + type + "\","
            + "\"occurredAt\":\"" + Instant.now() + "\","
            + "\"producer\":\"identity\","
            + "\"payload\":" + payload
            + "}";
    }

    /**
     * Stable ids shared with {@code IdentityAnalyticsReconcile} so seed backfill and live
     * publishes do not double-count the same from→to pair.
     */
    static String friendEventId(String kind, String fromUserId, String toUserId) {
        return UUID.nameUUIDFromBytes(
            ("halozalo:" + kind + ":" + fromUserId + ":" + toUserId).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    /** Stable id shared with reconcile {@code deterministicId("grp", groupId)}. */
    public static String groupEventId(String groupId) {
        return UUID.nameUUIDFromBytes(
            ("halozalo:grp:" + groupId).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
