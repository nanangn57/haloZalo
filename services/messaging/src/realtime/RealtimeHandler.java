package realtime;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws is push-only. Clients send messages over HTTP, where clientMsgId makes retries safe.
 * Anything a client sends only counts as a sign of life.
 */
public final class RealtimeHandler extends AbstractWebSocketHandler {
    private final RealtimeSessions sessions;

    public RealtimeHandler(RealtimeSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.open(session, (String) session.getAttributes().get(UserHandshakeInterceptor.USER_ID));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessions.seen(session.getId());
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
}
