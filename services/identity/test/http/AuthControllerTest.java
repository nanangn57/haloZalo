package http;

import account.Account;
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

import com.jayway.jsonpath.JsonPath;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {
    private MockMvc mvc;
    private MemoryMail mail;

    @BeforeEach
    void setUp() {
        mail = new MemoryMail();
        AuthService auth = new AuthService(
            new BcryptPasswordHasher(),
            new MemoryAccounts(),
            new MemorySessions(),
            userId -> { },
            new MemoryVerifications(),
            mail,
            Clock.systemUTC(),
            "http://localhost:8080"
        );
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth))
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    @Test
    void registerAcceptsEmailWithoutToken() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value("anh@example.com"))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        registerNgocAnh();

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"other@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
            .andExpect(jsonPath("$.error.message").value("Username or email already exists"));
    }

    @Test
    void verifyReturnsUserWithoutPasswordHash() throws Exception {
        registerNgocAnh();

        mvc.perform(get("/auth/verify").param("token", verificationToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("ngoc_anh"))
            .andExpect(jsonPath("$.email").value("anh@example.com"))
            .andExpect(jsonPath("$.userId").isNotEmpty())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void verifyRejectsUnknownToken() throws Exception {
        mvc.perform(get("/auth/verify").param("token", "missing"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Verification link is invalid or expired"));
    }

    @Test
    void loginBeforeVerifyIsUnauthorized() throws Exception {
        registerNgocAnh();

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"password1\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.error.message").value("Invalid login or password"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        registerNgocAnh();
        verifyNgocAnh();

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.error.message").value("Invalid login or password"));
    }

    @Test
    void meRejectsMissingToken() throws Exception {
        mvc.perform(get("/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.error.message").value("Missing or invalid access token"));
    }

    @Test
    void meReturnsTheVerifiedUser() throws Exception {
        registerNgocAnh();
        MvcResult verified = verifyNgocAnh();
        String userId = JsonPath.read(verified.getResponse().getContentAsString(), "$.userId");
        MvcResult loggedIn = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"password1\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String token = JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.username").value("ngoc_anh"))
            .andExpect(jsonPath("$.email").value("anh@example.com"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void sessionCheckIsNotAnHttpEndpoint() throws Exception {
        mvc.perform(get("/internal/sessions/current").header("Authorization", "Bearer token"))
            .andExpect(status().isNotFound());
    }

    private MvcResult registerNgocAnh() throws Exception {
        return mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isAccepted())
            .andReturn();
    }

    private MvcResult verifyNgocAnh() throws Exception {
        return mvc.perform(get("/auth/verify").param("token", verificationToken()))
            .andExpect(status().isOk())
            .andReturn();
    }

    private String verificationToken() {
        String body = mail.lastBody();
        return body.substring(body.indexOf("token=") + "token=".length());
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

    private static final class MemoryMail implements MailSender {
        private final List<String> bodies = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            bodies.add(body);
        }

        private String lastBody() {
            return bodies.get(bodies.size() - 1);
        }
    }
}
