package service;

import account.Account;
import auth.LoginRequest;
import auth.RegisterAccepted;
import auth.RegisterRequest;
import auth.TokenResponse;
import event.EventPublisher;
import mail.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import password.BcryptPasswordHasher;
import password.PasswordHasher;
import session.Session;
import storage.AccountRepository;
import storage.SessionRepository;
import storage.VerificationRepository;
import verification.EmailVerification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String BASE_URL = "http://localhost:8080";

    private MemoryAccounts accounts;
    private MemorySessions sessions;
    private MemoryEvents events;
    private MemoryVerifications verifications;
    private MemoryMail mail;
    private MutableClock clock;
    private PasswordHasher passwordHasher;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        sessions = new MemorySessions();
        events = new MemoryEvents();
        verifications = new MemoryVerifications();
        mail = new MemoryMail();
        clock = new MutableClock(NOW);
        passwordHasher = new BcryptPasswordHasher();
        auth = new AuthService(passwordHasher, accounts, sessions, events, verifications, mail, clock, BASE_URL);
    }

    @Test
    void registerStoresPendingVerificationAndSendsLink() {
        RegisterAccepted accepted = auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));

        assertEquals("anh@example.com", accepted.getEmail());
        assertNull(accounts.findByUsername("ngoc_anh"));
        assertEquals(0, sessions.size());
        assertEquals(0, events.userIds.size());
        EmailVerification pending = verifications.findByEmail("anh@example.com");
        assertEquals("ngoc_anh", pending.getUsername());
        assertEquals(NOW, pending.getCreatedAt());
        assertEquals(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS), pending.getExpiresAt());
        assertNotEquals("password1", pending.getPasswordHash());
        assertEquals("anh@example.com", mail.last().to);
        assertEquals("Verify your email", mail.last().subject);
        assertEquals(BASE_URL + "/auth/verify?token=" + pending.getToken(), mail.last().body);
        assertFalse(mail.last().body.contains("password1"));
    }

    @Test
    void registerRejectsDuplicateUsernameOrEmailWhilePending() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        String token = verifications.findByEmail("anh@example.com").getToken();

        AuthService.Rejected username = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("ngoc_anh", "other@example.com", "password1")));
        AuthService.Rejected email = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("other_name", "anh@example.com", "password1")));

        assertEquals(AuthService.Rejected.Reason.CONFLICT, username.getReason());
        assertEquals(AuthService.Rejected.Reason.CONFLICT, email.getReason());
        assertEquals(token, verifications.findByEmail("anh@example.com").getToken());
        assertEquals(1, mail.size());
    }

    @Test
    void registerRejectsUsernameOrEmailOfExistingAccount() {
        verifyNgocAnh();

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
        assertEquals(0, verifications.size());
        assertEquals(0, mail.size());
    }

    @Test
    void registerRejectsInvalidEmail() {
        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("ngoc_anh", "not-an-email", "password1")));

        assertEquals(AuthService.Rejected.Reason.VALIDATION, rejected.getReason());
        assertEquals(0, sessions.size());
        assertEquals(0, verifications.size());
    }

    @Test
    void registerDropsPendingRowWhenMailFails() {
        mail.failNext = true;

        assertThrows(IllegalStateException.class,
            () -> auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1")));

        assertNull(verifications.findByEmail("anh@example.com"));
        assertNull(accounts.findByUsername("ngoc_anh"));
    }

    @Test
    void registerReplacesExpiredVerification() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        String oldToken = verifications.findByEmail("anh@example.com").getToken();
        clock.setInstant(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS + 1));

        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password2"));

        assertNull(verifications.findByToken(oldToken));
        EmailVerification pending = verifications.findByEmail("anh@example.com");
        assertNotEquals(oldToken, pending.getToken());
        AuthService.Rejected oldLink = assertThrows(AuthService.Rejected.class, () -> auth.verify(oldToken));
        Account account = auth.verify(pending.getToken());
        assertEquals(AuthService.Rejected.Reason.VALIDATION, oldLink.getReason());
        assertTrue(passwordHasher.matches("password2", account.getPasswordHash()));
        assertEquals(account.getUserId(), accounts.findByUsername("ngoc_anh").getUserId());
    }

    @Test
    void registerReplacesSeparateExpiredUsernameAndEmailRows() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        clock.setInstant(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS + 1));
        auth.register(new RegisterRequest("other_name", "other@example.com", "password1"));
        clock.setInstant(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS * 2 + 2));

        auth.register(new RegisterRequest("ngoc_anh", "other@example.com", "password1"));

        assertNull(verifications.findByUsername("other_name"));
        assertNull(verifications.findByEmail("anh@example.com"));
        assertEquals("ngoc_anh", verifications.findByEmail("other@example.com").getUsername());
    }

    @Test
    void verifyCreatesAccountAndRejectsReuse() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        String token = verifications.findByEmail("anh@example.com").getToken();

        Account account = auth.verify(token);

        assertEquals("ngoc_anh", account.getUsername());
        assertEquals("anh@example.com", account.getEmail());
        assertEquals(NOW, account.getCreatedAt());
        assertNull(verifications.findByToken(token));
        assertEquals(0, sessions.size());
        AuthService.Rejected reused = assertThrows(AuthService.Rejected.class, () -> auth.verify(token));
        assertEquals(AuthService.Rejected.Reason.VALIDATION, reused.getReason());
        assertTrue(passwordHasher.matches("password1", accounts.findByUsername("ngoc_anh").getPasswordHash()));
    }

    @Test
    void verifyRejectsWhenUsernameOrEmailAlreadyExists() {
        verifyNgocAnh();
        verifications.save(new EmailVerification(
            "token-2",
            "ngoc_anh",
            "other@example.com",
            "hash",
            NOW,
            NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS)
        ));

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class, () -> auth.verify("token-2"));

        assertEquals(AuthService.Rejected.Reason.CONFLICT, rejected.getReason());
        assertEquals("token-2", verifications.findByToken("token-2").getToken());
        assertNull(accounts.findByEmail("other@example.com"));
    }

    @Test
    void verifyRejectsExpiredAndUnknownToken() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        String token = verifications.findByEmail("anh@example.com").getToken();
        clock.setInstant(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS));

        AuthService.Rejected expired = assertThrows(AuthService.Rejected.class, () -> auth.verify(token));
        AuthService.Rejected unknown = assertThrows(AuthService.Rejected.class, () -> auth.verify("missing"));
        AuthService.Rejected blank = assertThrows(AuthService.Rejected.class, () -> auth.verify("  "));

        assertEquals(AuthService.Rejected.Reason.VALIDATION, expired.getReason());
        assertEquals(AuthService.Rejected.Reason.VALIDATION, unknown.getReason());
        assertEquals(AuthService.Rejected.Reason.VALIDATION, blank.getReason());
        assertNull(accounts.findByUsername("ngoc_anh"));
        assertEquals(token, verifications.findByToken(token).getToken());
    }

    @Test
    void loginBeforeVerifyRejectsWithoutSession() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        int before = sessions.size();

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.login(new LoginRequest("anh@example.com", "password1")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(0, events.userIds.size());
    }

    @Test
    void loginOpensAnotherSessionAndPublishesEvent() {
        Account account = verifyNgocAnh();
        Instant accountCreatedAt = account.getCreatedAt();

        TokenResponse first = auth.login(new LoginRequest("anh@example.com", "password1"));
        TokenResponse second = auth.login(new LoginRequest("anh@example.com", "password1"));

        Session loggedInSession = sessions.findById(second.getAccessToken());
        assertNotEquals(first.getAccessToken(), second.getAccessToken());
        assertEquals(accountCreatedAt, accounts.findByUsername("ngoc_anh").getCreatedAt());
        assertFalse(loggedInSession.getCreatedAt().isBefore(accountCreatedAt));
        assertEquals(loggedInSession.getCreatedAt().plusSeconds(AuthService.SESSION_TTL_SECONDS), loggedInSession.getExpiresAt());
        assertEquals(account.getUserId(), auth.currentUserId(first.getAccessToken()));
        assertEquals(account.getUserId(), auth.currentUserId(second.getAccessToken()));
        assertEquals(List.of(account.getUserId(), account.getUserId()), events.userIds);
    }

    @Test
    void loginRejectsWrongPasswordWithoutNewSession() {
        verifyNgocAnh();
        int before = sessions.size();

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.login(new LoginRequest("ngoc_anh", "wrong-password")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(0, events.userIds.size());
    }

    @Test
    void loginRejectsUnknownUserWithoutNewSession() {
        int before = sessions.size();

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.login(new LoginRequest("missing_user", "password1")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(0, events.userIds.size());
    }

    @Test
    void loginByUsernameOpensASession() {
        Account account = verifyNgocAnh();

        TokenResponse loggedIn = auth.login(new LoginRequest("ngoc_anh", "password1"));

        assertEquals(account.getUserId(), auth.currentUserId(loggedIn.getAccessToken()));
    }

    @Test
    void currentUserIdRejectsMissingAndExpiredSession() {
        AuthService.Rejected missing = assertThrows(AuthService.Rejected.class, () -> auth.currentUserId("missing"));
        sessions.save(new Session("old-session", "user-1", Instant.parse("2020-01-01T00:00:00Z"), Instant.now().minusSeconds(1)));
        AuthService.Rejected expired = assertThrows(AuthService.Rejected.class, () -> auth.currentUserId("old-session"));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, missing.getReason());
        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, expired.getReason());
    }

    private Account verifyNgocAnh() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        return auth.verify(verifications.findByEmail("anh@example.com").getToken());
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

        int size() {
            return rows.size();
        }
    }

    private static final class MemoryMail implements MailSender {
        private final List<Sent> sent = new ArrayList<>();
        private boolean failNext;

        @Override
        public void send(String to, String subject, String body) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Could not send mail");
            }
            sent.add(new Sent(to, subject, body));
        }

        Sent last() {
            return sent.get(sent.size() - 1);
        }

        int size() {
            return sent.size();
        }
    }

    private static final class Sent {
        private final String to;
        private final String subject;
        private final String body;

        private Sent(String to, String subject, String body) {
            this.to = to;
            this.subject = subject;
            this.body = body;
        }
    }

    private static final class MemoryEvents implements EventPublisher {
        private final List<String> userIds = new ArrayList<>();

        @Override
        public void publishLoggedIn(String userId) {
            userIds.add(userId);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
