package identity.http;

import identity.account.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import identity.service.AuthService;
import identity.service.UserSearchService;
import identity.session.Session;
import identity.storage.AccountRepository;
import identity.storage.SessionRepository;
import identity.storage.VerificationRepository;
import identity.verification.EmailVerification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import identity.mail.MailSender;
import identity.password.BcryptPasswordHasher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {
    private MockMvc mvc;
    private MemoryAccounts accounts;
    private MemoryMail mail;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        mail = new MemoryMail();
        AuthService auth = new AuthService(
            new BcryptPasswordHasher(),
            accounts,
            new MemorySessions(),
            userId -> { },
            new MemoryVerifications(),
            mail,
            Clock.systemUTC(),
            "http://localhost:8080"
        );
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth), new UserController(new UserSearchService(accounts)))
            .addFilters(new SessionFilter(auth, new ObjectMapper(), (userId, path) -> { }))
            .setControllerAdvice(new AuthExceptionHandler(), new UserExceptionHandler())
            .build();
    }

    @Test
    void missingTokenDoesNotReturnAUser() throws Exception {
        accounts.save(new Account("user-1", "ngoc_anh", "anh@example.com", "hash", Instant.now()));

        mvc.perform(get("/users").param("q", "ngoc_anh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void findsUserByUsernameWhenTheTokenIsValid() throws Exception {
        String accessToken = registerAndLogin();

        mvc.perform(get("/users").param("q", "ngoc_anh").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("ngoc_anh"))
            .andExpect(jsonPath("$.email").value("anh@example.com"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        String accessToken = registerAndLogin();

        mvc.perform(get("/users").param("q", "missing").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private String registerAndLogin() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isAccepted());
        String token = mail.body.substring(mail.body.indexOf("token=") + "token=".length());
        mvc.perform(get("/auth/verify").param("token", token)).andExpect(status().isOk());
        return com.jayway.jsonpath.JsonPath.read(
            mvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"ngoc_anh\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.accessToken"
        );
    }

    private static final class MemoryMail implements MailSender {
        private String body;

        @Override
        public void send(String to, String subject, String text) {
            this.body = text;
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
