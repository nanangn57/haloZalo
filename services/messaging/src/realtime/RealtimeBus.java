package realtime;

/**
 * Hands a frame to the sockets that should get it. Sockets live in this process, so this is a direct call.
 */
public interface RealtimeBus {
    void publish(RealtimeFrame frame);
}
