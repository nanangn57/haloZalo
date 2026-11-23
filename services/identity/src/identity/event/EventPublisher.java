package identity.event;

public interface EventPublisher {
    void publishLoggedIn(String userId);

    default void publishRegistered(String userId) {
    }

    default void publishVerified(String userId) {
    }

    default void publishGroupCreated(String groupId, String ownerId) {
        publishGroupCreated(groupId, ownerId, 1);
    }

    default void publishGroupCreated(String groupId, String ownerId, int memberCount) {
    }

    default void publishFriendRequestSent(String fromUserId, String toUserId) {
    }

    default void publishFriendRequestAccepted(String fromUserId, String toUserId) {
    }

    /** The owner added a member. */
    default void publishMemberAdded(String groupId, String actorId, String memberId) {
    }

    /** The owner removed a member. */
    default void publishMemberRemoved(String groupId, String actorId, String memberId) {
    }

    /** A member left by their own choice. */
    default void publishMemberLeft(String groupId, String memberId) {
    }

    default void publishOwnerTransferred(String groupId, String fromUserId, String toUserId) {
    }

    default void publishGroupDissolved(String groupId, String actorId) {
    }
}
