package realtime;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws receives ephemeral client signals (typing/presence) and pushes server envelopes.
 * Durable messages still go over HTTP with clientMsgId for safe retries.
 */
public final class RealtimeHandler extends AbstractWebSocketHandler {
    private final RealtimeSessions sessions;
    private final ClientSignalRelay signals;

    public RealtimeHandler(RealtimeSessions sessions, ClientSignalRelay signals) {
        this.sessions = sessions;
        this.signals = signals;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.open(session, (String) session.getAttributes().get(UserHandshakeInterceptor.USER_ID));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessions.seen(session.getId());
        signals.handle(userIdOf(session), message.getPayload());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        sessions.seen(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        sessions.close(session.getId(), CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.forget(session.getId());
    }

    private static String userIdOf(WebSocketSession session) {
        Object fromHandshake = session.getAttributes().get(UserHandshakeInterceptor.USER_ID);
        if (fromHandshake instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }
}
