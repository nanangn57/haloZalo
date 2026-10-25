package identity.http;

import identity.access.ConversationAccess;
import identity.access.MembershipRequestAuthorizer;
import identity.account.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import identity.group.MembershipLookup;
import com.jayway.jsonpath.JsonPath;
import identity.mail.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import identity.password.BcryptPasswordHasher;
import identity.service.AuthService;
import identity.session.Session;
import identity.storage.AccountRepository;
import identity.storage.SessionRepository;
import identity.storage.VerificationRepository;
import identity.verification.EmailVerification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final Set<String> groups = new HashSet<>();
    private final Set<String> chats = new HashSet<>();

    @BeforeEach
    void setUp() {
        sessions = new MemorySessions();
        mail = new MemoryMail();
        groups.clear();
        chats.clear();
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
        MembershipLookup memberships = (groupId, userId) -> groups.contains(groupId);
        ConversationAccess conversations = (userId, conversationId) -> chats.contains(conversationId);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth), new ProbeController())
            .addFilters(new SessionFilter(auth, new ObjectMapper(), new MembershipRequestAuthorizer(memberships, conversations)))
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

    @Test
    void webSocketUsesTheAccessTokenQuery() throws Exception {
        String accessToken = login();

        mvc.perform(get("/ws").param("access_token", accessToken))
            .andExpect(status().isOk())
            .andExpect(content().string(userIdOf(accessToken)));

        mvc.perform(get("/ws").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void groupAndChatWithoutMembershipStopAtTheFilter() throws Exception {
        String accessToken = login();
        String groupId = "11111111-2222-4333-8444-555555555555";
        String conversationId = "99999999-8888-4777-8666-555555555555";

        mvc.perform(post("/groups/" + groupId + "/members").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.message").value("Not a member of this conversation"));

        groups.add(groupId);
        chats.add(conversationId);
        mvc.perform(post("/groups/" + groupId + "/members").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(content().string("group"));
        mvc.perform(post("/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(content().string("chat"));
        mvc.perform(post("/conversations/direct").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(content().string("direct"));
    }

    private String login() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ngoc_anh\",\"email\":\"anh@example.com\",\"password\":\"password1\"}"))
            .andExpect(status().isAccepted());
        mvc.perform(get("/auth/verify").param("token", verificationToken()))
            .andExpect(status().isOk());
        MvcResult loggedIn = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ngoc_anh\",\"password\":\"password1\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.accessToken");
    }

    private String userIdOf(String accessToken) {
        return sessions.findById(accessToken).getUserId();
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe(@RequestAttribute(SessionFilter.USER_ID) String userId) {
            return userId;
        }

        @GetMapping("/ws")
        String socket(@RequestAttribute(SessionFilter.USER_ID) String userId) {
            return userId;
        }

        @PostMapping("/groups/{groupId}/members")
        String group(@PathVariable String groupId) {
            return "group";
        }

        @PostMapping("/conversations/{conversationId}/messages")
        String chat(@PathVariable String conversationId) {
            return "chat";
        }

        @PostMapping("/conversations/direct")
        String direct() {
            return "direct";
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
