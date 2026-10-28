package realtime;

import http.CurrentUser;
import service.Ids;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Takes the user from {@link CurrentUser}, exactly like HTTP. In the app, the session filter checks the
 * access_token query parameter before the upgrade. Without a user the upgrade is refused with 401, as the
 * contract says for /ws.
 */
public final class UserHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ID = "userId";

    private final CurrentUser currentUser;

    public UserHandshakeInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes) {
        // Same spelling as the member ids read from Postgres, so frames find this socket.
        String given = request instanceof ServletServerHttpRequest servlet ? currentUser.of(servlet.getServletRequest()) : null;
        String userId = Ids.canonical(given);
        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception failure) {
    }
}
