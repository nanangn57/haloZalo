package identity.http;

import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsConfigTest {
    private static final String WEB = "http://localhost:5173";
    private static final List<String> ORIGINS = List.of("http://localhost:*", "http://127.0.0.1:*");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .addFilters(CorsConfig.filter(ORIGINS), new RejectUnauthenticated())
            .build();
    }

    @Test
    void browserCorsRunsBeforeTheSessionFilter() {
        CorsSettings settings = new CorsSettings();
        assertEquals(Ordered.HIGHEST_PRECEDENCE, new CorsConfig().browserCors(settings).getOrder());
    }

    @Test
    void preflightFromTheWebIsAllowedBeforeAuth() throws Exception {
        mvc.perform(options("/auth/login")
                .header(HttpHeaders.ORIGIN, WEB)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("POST")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, Matchers.containsString("content-type")))
            .andExpect(header().doesNotExist("X-Filter-Reached"));
    }

    @Test
    void loginResponseFromTheWebIncludesAllowOrigin() throws Exception {
        mvc.perform(post("/auth/login")
                .header(HttpHeaders.ORIGIN, WEB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"password1\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB));
    }

    @Test
    void unauthorizedResponseStillExposesAllowOrigin() throws Exception {
        mvc.perform(get("/me").header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5173"));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mvc.perform(options("/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestWithoutOriginSkipsCors() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @RestController
    static class ProbeController {
        @PostMapping("/auth/login")
        void login() {
        }

        @GetMapping("/me")
        void me() {
        }
    }

    private static final class RejectUnauthenticated extends OncePerRequestFilter {
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !"/me".equals(request.getRequestURI());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
            response.setHeader("X-Filter-Reached", "true");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
