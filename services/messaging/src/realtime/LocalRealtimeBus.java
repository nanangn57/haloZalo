package realtime;

import org.springframework.stereotype.Component;

/**
 * Delivers to the sockets held by this process. The app runs as one process, so every socket is here.
 */
@Component
public final class LocalRealtimeBus implements RealtimeBus {
    private final RealtimeSessions sessions;

    public LocalRealtimeBus(RealtimeSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void publish(RealtimeFrame frame) {
        sessions.deliver(frame.recipients(), frame.envelope());
    }
}
