package identity.event;

public interface EventPublisher {
    void publishLoggedIn(String userId);

    default void publishGroupCreated(String groupId, String ownerId) {
    }
}
