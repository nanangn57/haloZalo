package realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Open sockets on this instance, by user. One user may have many sockets: web and mobile, several tabs.
 */
@Component
public final class RealtimeSessions implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(RealtimeSessions.class);

    private final RealtimeProperties properties;
    private final Clock clock;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionIdsByUser = new ConcurrentHashMap<>();
    private ScheduledExecutorService heartbeat;

    @Autowired
    public RealtimeSessions(RealtimeProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RealtimeSessions(RealtimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void open(WebSocketSession session, String userId) {
        // Sends from several threads are serialized. A client that stops reading is cut off instead of
        // holding a thread or growing memory; it reconnects and catches up by seq.
        WebSocketSession guarded = new ConcurrentWebSocketSessionDecorator(
            session,
            (int) properties.sendTimeLimit().toMillis(),
            (int) properties.sendBufferLimit().toBytes(),
            ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        connections.put(session.getId(), new Connection(userId, guarded, clock.instant()));
        sessionIdsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session.getId());
    }

    public void seen(String sessionId) {
        connections.computeIfPresent(sessionId, (id, connection) -> connection.seenAt(clock.instant()));
    }

    public void deliver(Collection<String> userIds, String envelope) {
        TextMessage frame = new TextMessage(envelope);
        for (String userId : userIds) {
            for (String sessionId : sessionIdsByUser.getOrDefault(userId, Set.of())) {
                send(sessionId, frame);
            }
        }
    }

    /**
     * Pings every socket, and closes those that have not answered within the pong timeout.
     * Mobile networks drop connections without a close frame; this is how the server notices.
     */
    void beat() {
        Instant deadline = clock.instant().minus(properties.pongTimeout());
        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            if (entry.getValue().lastSeen().isBefore(deadline)) {
                close(entry.getKey(), CloseStatus.SESSION_NOT_RELIABLE);
            } else {
                send(entry.getKey(), new PingMessage());
            }
        }
    }

    public void close(String sessionId, CloseStatus status) {
        Connection connection = forget(sessionId);
        if (connection == null) {
            return;
        }
        try {
            connection.session().close(status);
        } catch (IOException | RuntimeException ex) {
            log.debug("Socket {} was already gone", sessionId, ex);
        }
    }

    public Connection forget(String sessionId) {
        Connection connection = connections.remove(sessionId);
        if (connection != null) {
            sessionIdsByUser.computeIfPresent(connection.userId(), (userId, ids) -> {
                ids.remove(sessionId);
                return ids.isEmpty() ? null : ids;
            });
        }
        return connection;
    }

    public int count(String userId) {
        return sessionIdsByUser.getOrDefault(userId, Set.of()).size();
    }

    private void send(String sessionId, WebSocketMessage<?> message) {
        Connection connection = connections.get(sessionId);
        if (connection == null) {
            return;
        }
        try {
            connection.session().sendMessage(message);
        } catch (IOException | RuntimeException ex) {
            // Includes SessionLimitExceededException for a client that cannot keep up.
            close(sessionId, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    @Override
    public void start() {
        heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        long every = properties.pingInterval().toMillis();
        heartbeat.scheduleAtFixedRate(this::beatSafely, every, every, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs before the web server stops, so clients get 1001 and reconnect to another instance.
     */
    @Override
    public void stop() {
        heartbeat.shutdownNow();
        heartbeat = null;
        for (String sessionId : List.copyOf(connections.keySet())) {
            close(sessionId, CloseStatus.GOING_AWAY);
        }
    }

    @Override
    public boolean isRunning() {
        return heartbeat != null;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void beatSafely() {
        try {
            beat();
        } catch (RuntimeException ex) {
            log.warn("Heartbeat failed", ex);
        }
    }

    public record Connection(String userId, WebSocketSession session, Instant lastSeen) {
        Connection seenAt(Instant now) {
            return new Connection(userId, session, now);
        }
    }
}
