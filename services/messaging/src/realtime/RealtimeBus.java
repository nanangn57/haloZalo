package realtime;

/**
 * Carries a frame to every instance that may hold a recipient's socket.
 */
public interface RealtimeBus {
    void publish(RealtimeFrame frame);
}
