package presence;

/**
 * Told when a socket of a user opens or closes.
 */
public interface PresenceTracker {
    void connected(String userId);

    void disconnected(String userId);
}
