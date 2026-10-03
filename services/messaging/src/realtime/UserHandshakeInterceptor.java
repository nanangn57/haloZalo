package realtime;

import http.MessageController;
import service.Ids;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * The gateway checks access_token with Identity and forwards the user in the same header as HTTP.
 * Without it the upgrade is refused with 401, as the contract says for /ws.
 */
public final class UserHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ID = "userId";

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes) {
        // Same spelling as the member ids read from Postgres, so frames find this socket.
        String userId = Ids.canonical(request.getHeaders().getFirst(MessageController.USER_HEADER));
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
