package http;

import auth.ErrorResponse;
import service.AuthService;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class SessionFilter extends OncePerRequestFilter {
    public static final String USER_ID = "userId";
    private static final String BEARER = "Bearer ";

    private final AuthService auth;
    private final ObjectMapper json;

    public SessionFilter(AuthService auth, ObjectMapper json) {
        this.auth = auth;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/auth/register".equals(path) || "/auth/login".equals(path) || "/auth/verify".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            String userId = auth.currentUserId(sessionId(request.getHeader("Authorization")));
            request.setAttribute(USER_ID, userId);
            chain.doFilter(request, response);
        } catch (AuthService.Rejected rejected) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getWriter(), new ErrorResponse("UNAUTHENTICATED", rejected.getMessage()));
        }
    }

    private static String sessionId(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return "";
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
