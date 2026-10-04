package realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Delivers straight to sockets on this instance. Only correct with a single instance.
 */
@Component
@ConditionalOnProperty(name = "messaging.realtime.bus", havingValue = "local")
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
