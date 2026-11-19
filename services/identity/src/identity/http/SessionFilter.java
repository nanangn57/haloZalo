package identity.http;

import identity.access.RequestAuthorizer;
import identity.auth.ErrorResponse;
import identity.service.AuthService;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class SessionFilter extends OncePerRequestFilter {
    public static final String USER_ID = "userId";
    private static final String BEARER = "Bearer ";

    private static final String ACCESS_TOKEN = "access_token";

    private final AuthService auth;
    private final ObjectMapper json;
    private final RequestAuthorizer access;

    public SessionFilter(AuthService auth, ObjectMapper json, RequestAuthorizer access) {
        this.auth = auth;
        this.json = json;
        this.access = access;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/auth/register".equals(path) || "/auth/login".equals(path) || "/auth/verify".equals(path);
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    )
        throws ServletException, IOException {
        try {
            String userId = auth.currentUserId(sessionId(request));
            request.setAttribute(USER_ID, userId);
            access.authorize(userId, request.getRequestURI());
            chain.doFilter(request, response);
        } catch (AuthService.Rejected rejected) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", rejected.getMessage());
        } catch (RequestAuthorizer.Forbidden forbidden) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", forbidden.getMessage());
        }
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), new ErrorResponse(code, message));
    }

    private static String sessionId(HttpServletRequest request) {
        if (isWebSocket(request.getRequestURI())) {
            String token = request.getParameter(ACCESS_TOKEN);
            return token == null ? "" : token.trim();
        }
        return bearer(request.getHeader("Authorization"));
    }

    private static boolean isWebSocket(String path) {
        return "/ws".equals(path) || (path != null && path.startsWith("/ws/"));
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return "";
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
