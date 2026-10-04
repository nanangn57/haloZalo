package http;

import account.Account;
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

import com.jayway.jsonpath.JsonPath;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AuthService auth = new AuthService(
            new BcryptPasswordHasher(),
            new MemoryAccounts(),
            new MemorySessions(),
            userId -> { }
        );
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth))
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    @Test
    void registerReturnsNestedUserWithoutPasswordHash() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(259200))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value("ngoc_anh"))
            .andExpect(jsonPath("$.user.email").value("anh@example.com"))
            .andExpect(jsonPath("$.user.userId").isNotEmpty())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
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
    void loginRejectsWrongPassword() throws Exception {
        registerNgocAnh();

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
    void meReturnsTheRegisteredUser() throws Exception {
        MvcResult registered = registerNgocAnh();
        String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        String userId = JsonPath.read(registered.getResponse().getContentAsString(), "$.user.userId");

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
            .andExpect(status().isCreated())
            .andReturn();
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
}
