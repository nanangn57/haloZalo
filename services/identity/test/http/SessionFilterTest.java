package http;

import account.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import mail.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import password.BcryptPasswordHasher;
import service.AuthService;
import session.Session;
import storage.AccountRepository;
import storage.SessionRepository;
import storage.VerificationRepository;
import verification.EmailVerification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionFilterTest {
    private MockMvc mvc;
    private MemorySessions sessions;
    private MemoryMail mail;

    @BeforeEach
    void setUp() {
        sessions = new MemorySessions();
        mail = new MemoryMail();
        AuthService auth = new AuthService(
            new BcryptPasswordHasher(),
            new MemoryAccounts(),
            sessions,
            userId -> { },
            new MemoryVerifications(),
            mail,
            Clock.systemUTC(),
            "http://localhost:8080"
        );
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth), new ProbeController())
            .addFilters(new SessionFilter(auth, new ObjectMapper()))
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    @Test
    void missingUnknownAndExpiredTokensAreRejected() throws Exception {
        mvc.perform(get("/probe"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/probe").header("Authorization", "Bearer missing"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        sessions.save(new Session("old-session", "user-1", Instant.parse("2020-01-01T00:00:00Z"), Instant.now().minusSeconds(1)));
        mvc.perform(get("/probe").header("Authorization", "Bearer old-session"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void validTokenReachesHandlerWithUserId() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isAccepted());
        String token = verificationToken();
        mvc.perform(get("/auth/verify").param("token", token))
            .andExpect(status().isOk());
        MvcResult loggedIn = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"password1\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.accessToken");
        String userId = JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.user.userId");

        mvc.perform(get("/probe").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(content().string(userId));
    }

    @Test
    void registerLoginAndVerifySkipTheFilter() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/auth/verify"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private String verificationToken() {
        String body = mail.lastBody();
        return body.substring(body.indexOf("token=") + "token=".length());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe(@RequestAttribute(SessionFilter.USER_ID) String userId) {
            return userId;
        }
    }

    private final class MemoryMail implements MailSender {
        private String body;

        @Override
        public void send(String to, String subject, String text) {
            this.body = text;
        }

        String lastBody() {
            return body;
        }
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final List<Account> rows = new ArrayList<>();

        @Override
        public void save(Account account) {
            rows.add(account);
        }

        @Override
        public Account findByUsername(String username) {
            return rows.stream().filter(row -> row.getUsername().equals(username)).findFirst().orElse(null);
        }

        @Override
        public Account findByEmail(String email) {
            return rows.stream().filter(row -> row.getEmail().equals(email)).findFirst().orElse(null);
        }

        @Override
        public Account findById(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).findFirst().orElse(null);
        }
    }

    private static final class MemorySessions implements SessionRepository {
        private final List<Session> rows = new ArrayList<>();

        @Override
        public void save(Session session) {
            rows.add(session);
        }

        @Override
        public Session findById(String sessionId) {
            return rows.stream().filter(row -> row.getSessionId().equals(sessionId)).findFirst().orElse(null);
        }
    }

    private static final class MemoryVerifications implements VerificationRepository {
        private final List<EmailVerification> rows = new ArrayList<>();

        @Override
        public void save(EmailVerification verification) {
            rows.add(verification);
        }

        @Override
        public EmailVerification findByToken(String token) {
            return rows.stream().filter(row -> row.getToken().equals(token)).findFirst().orElse(null);
        }

        @Override
        public EmailVerification findByUsername(String username) {
            return rows.stream().filter(row -> row.getUsername().equals(username)).findFirst().orElse(null);
        }

        @Override
        public EmailVerification findByEmail(String email) {
            return rows.stream().filter(row -> row.getEmail().equals(email)).findFirst().orElse(null);
        }

        @Override
        public void delete(String token) {
            rows.removeIf(row -> row.getToken().equals(token));
        }
    }
}
