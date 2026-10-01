package service;

import account.Account;
import auth.LoginRequest;
import auth.RegisterRequest;
import auth.TokenResponse;
import event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import password.BcryptPasswordHasher;
import session.Session;
import storage.AccountRepository;
import storage.SessionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    private MemoryAccounts accounts;
    private MemorySessions sessions;
    private MemoryEvents events;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        sessions = new MemorySessions();
        events = new MemoryEvents();
        auth = new AuthService(new BcryptPasswordHasher(), accounts, sessions, events);
    }

    @Test
    void registerReturnsOpaqueSessionAndStoresHash() {
        TokenResponse response = auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));

        assertEquals("Bearer", response.getTokenType());
        assertEquals(AuthService.SESSION_TTL_SECONDS, response.getExpiresIn());
        assertEquals("ngoc_anh", response.getUser().getUsername());
        assertEquals("anh@example.com", response.getUser().getEmail());
        Account stored = accounts.findByUsername("ngoc_anh");
        assertNotEquals("password1", stored.getPasswordHash());
        assertEquals(response.getUser().getUserId(), auth.currentUserId(response.getAccessToken()));
        assertEquals(0, events.userIds.size());
    }

    @Test
    void registerRejectsDuplicateUsernameOrEmail() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));

        AuthService.Rejected username = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("ngoc_anh", "other@example.com", "password1")));
        AuthService.Rejected email = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("other_name", "anh@example.com", "password1")));

        assertEquals(AuthService.Rejected.Reason.CONFLICT, username.getReason());
        assertEquals(AuthService.Rejected.Reason.CONFLICT, email.getReason());
    }

    @Test
    void registerRejectsShortPassword() {
        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "short")));

        assertEquals(AuthService.Rejected.Reason.VALIDATION, rejected.getReason());
    }

    @Test
    void loginOpensAnotherSessionAndPublishesEvent() {
        TokenResponse registered = auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));

        TokenResponse loggedIn = auth.login(new LoginRequest("anh@example.com", "password1"));

        assertNotEquals(registered.getAccessToken(), loggedIn.getAccessToken());
        assertEquals(registered.getUser().getUserId(), auth.currentUserId(registered.getAccessToken()));
        assertEquals(registered.getUser().getUserId(), auth.currentUserId(loggedIn.getAccessToken()));
        assertEquals(List.of(registered.getUser().getUserId()), events.userIds);
    }

    @Test
    void loginRejectsWrongPasswordWithoutNewSession() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        int before = sessions.size();

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.login(new LoginRequest("ngoc_anh", "wrong-password")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(0, events.userIds.size());
    }

    @Test
    void currentUserIdRejectsMissingAndExpiredSession() {
        AuthService.Rejected missing = assertThrows(AuthService.Rejected.class, () -> auth.currentUserId("missing"));
        sessions.save(new Session("old-session", "user-1", Instant.now().minusSeconds(1)));
        AuthService.Rejected expired = assertThrows(AuthService.Rejected.class, () -> auth.currentUserId("old-session"));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, missing.getReason());
        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, expired.getReason());
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

        int size() {
            return rows.size();
        }
    }

    private static final class MemoryEvents implements EventPublisher {
        private final List<String> userIds = new ArrayList<>();

        @Override
        public void publishLoggedIn(String userId) {
            userIds.add(userId);
        }
    }
}
