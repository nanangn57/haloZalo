package http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that says who is calling, for HTTP and for the WebSocket handshake.
 * In the app, the session filter checks the access token with identity and sets the request attribute.
 * While messaging still runs on its own there is no such filter, so the header is accepted instead.
 * The header must be turned off (messaging.user-header-enabled: false) once the filter exists,
 * or any client could claim to be any user.
 */
@Component
public final class CurrentUser {
    /**
     * Request attribute the app's session filter sets to the caller's user id.
     */
    public static final String ATTRIBUTE = "userId";
    public static final String HEADER = "X-User-Id";

    private final boolean headerEnabled;

    public CurrentUser(@Value("${messaging.user-header-enabled:true}") boolean headerEnabled) {
        this.headerEnabled = headerEnabled;
    }

    /**
     * Returns the user id as given, or null when the request carries none. Callers check its format.
     */
    public String of(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String userId) {
            return userId;
        }
        return headerEnabled ? request.getHeader(HEADER) : null;
    }
}
