package http;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves Bearer / access_token against identity.sessions while messaging runs alone.
 * Disabled in profile {@code app}; identity {@code SessionFilter} owns auth in the monolith.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Profile("!app")
public final class SharedSessionFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";
    private static final String ACCESS_TOKEN = "access_token";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final boolean enabled;

    public SharedSessionFilter(
        JdbcTemplate jdbc,
        ObjectMapper json,
        @Value("${messaging.session-auth-enabled:false}") boolean enabled
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        if (path.startsWith("/actuator") || path.startsWith("/internal/")) {
            return true;
        }
        return "GET".equalsIgnoreCase(request.getMethod()) && path.matches("/media/[^/]+");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String sessionId = sessionId(request);
        if (sessionId.isEmpty()) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Missing or invalid access token");
            return;
        }
        String userId = lookupUserId(sessionId);
        if (userId == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Missing or invalid access token");
            return;
        }
        request.setAttribute(CurrentUser.ATTRIBUTE, userId);
        chain.doFilter(request, response);
    }

    private String lookupUserId(String sessionId) {
        return jdbc.query(
            "SELECT user_id FROM identity.sessions WHERE session_id = ? AND expires_at > ?",
            rs -> rs.next() ? rs.getString(1) : null,
            sessionId,
            Timestamp.from(Instant.now())
        );
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Map.of("error", Map.of("code", code, "message", message)));
    }

    private static String sessionId(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/ws".equals(path) || (path != null && path.startsWith("/ws/"))) {
            String token = request.getParameter(ACCESS_TOKEN);
            return token == null ? "" : token.trim();
        }
        return bearer(request.getHeader("Authorization"));
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return "";
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
