package identity.service;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.auth.LoginRequest;
import identity.auth.RegisterAccepted;
import identity.auth.RegisterRequest;
import identity.auth.TokenResponse;
import identity.event.EventPublisher;
import identity.mail.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import identity.password.BcryptPasswordHasher;
import identity.password.PasswordHasher;
import identity.session.Session;
import identity.account.Profile;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;
import identity.storage.SessionRepository;
import identity.storage.VerificationRepository;
import identity.verification.EmailVerification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String BASE_URL = "http://localhost:8080";

    private MemoryAccounts accounts;
    private MemoryProfiles profiles;
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
        profiles = new MemoryProfiles();
        sessions = new MemorySessions();
        events = new MemoryEvents();
        verifications = new MemoryVerifications();
        mail = new MemoryMail();
        clock = new MutableClock(NOW);
        passwordHasher = new BcryptPasswordHasher();
        auth = new AuthService(passwordHasher, accounts, profiles, sessions, events, verifications, mail, clock, BASE_URL);
    }

    @Test
    void registerStoresPendingVerificationAndSendsLink() {
        RegisterAccepted accepted = auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));

        assertEquals("anh@example.com", accepted.getEmail());
        assertEquals(RegisterAccepted.STATUS_PENDING_VERIFICATION, accepted.getStatus());
        assertNull(accounts.findByUsername("ngoc_anh"));
        assertEquals(0, sessions.size());
        assertEquals(0, events.userIds.size());
        EmailVerification pending = verifications.findByEmail("anh@example.com");
        assertEquals("ngoc_anh", pending.getUsername());
        assertEquals(NOW, pending.getCreatedAt());
        assertEquals(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS), pending.getExpiresAt());
        assertNotEquals("password1", pending.getPasswordHash());
        assertEquals("anh@example.com", mail.last().to);
        assertEquals("HaloZalo - Verify your email", mail.last().subject);
        assertTrue(mail.last().body.endsWith(BASE_URL + "/auth/verify?token=" + pending.getToken()));
        assertFalse(mail.last().body.contains("password1"));
    }

    @Test
    void registerImmediateCreatesAccountWithoutMail() {
        AuthService immediate = new AuthService(
            passwordHasher,
            accounts,
            profiles,
            sessions,
            events,
            verifications,
            mail,
            clock,
            BASE_URL,
            List.of(),
            AuthService.ACTIVATION_IMMEDIATE
        );

        RegisterAccepted accepted = immediate.register(
            new RegisterRequest("ngoc_anh", "anh@example.com", "password1")
        );

        assertEquals("anh@example.com", accepted.getEmail());
        assertEquals(RegisterAccepted.STATUS_ACTIVE, accepted.getStatus());
        assertNull(verifications.findByEmail("anh@example.com"));
        assertEquals(0, mail.size());
        Account account = accounts.findByUsername("ngoc_anh");
        assertEquals("anh@example.com", account.getEmail());
        assertTrue(passwordHasher.matches("password1", account.getPasswordHash()));
        TokenResponse login = immediate.login(new LoginRequest("anh@example.com", "password1", "test-device-1"));
        assertEquals(account.getUserId(), login.getUser().getUserId());
    }

    @Test
    void registerImmediateRejectsDuplicateAccount() {
        AuthService immediate = new AuthService(
            passwordHasher,
            accounts,
            profiles,
            sessions,
            events,
            verifications,
            mail,
            clock,
            BASE_URL,
            List.of(),
            AuthService.ACTIVATION_IMMEDIATE
        );
        immediate.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));

        AuthService.Rejected duplicate = assertThrows(
            AuthService.Rejected.class,
            () -> immediate.register(new RegisterRequest("ngoc_anh", "other@example.com", "password1"))
        );

        assertEquals(AuthService.Rejected.Reason.CONFLICT, duplicate.getReason());
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

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1")));

        assertEquals(AuthService.Rejected.Reason.VALIDATION, rejected.getReason());
        assertEquals("Could not send verification email", rejected.getMessage());
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
        AuthService.Rejected oldLink = assertThrows(AuthService.Rejected.class, () -> auth.verify(oldToken, "test-device-1", null));
        TokenResponse verified = auth.verify(pending.getToken(), "test-device-1", null);
        Account account = accounts.findByUsername("ngoc_anh");
        assertEquals(AuthService.Rejected.Reason.VALIDATION, oldLink.getReason());
        assertTrue(passwordHasher.matches("password2", account.getPasswordHash()));
        assertEquals(account.getUserId(), verified.getUser().getUserId());
        assertEquals(1, sessions.size());
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

        TokenResponse verified = auth.verify(token, "test-device-1", null);
        Account account = accounts.findByUsername("ngoc_anh");

        assertEquals("ngoc_anh", verified.getUser().getUsername());
        assertEquals("anh@example.com", verified.getUser().getEmail());
        assertEquals(identity.account.PlatformRole.USER, verified.getUser().getPlatformRole());
        assertEquals(NOW, account.getCreatedAt());
        assertNotNull(verified.getAccessToken());
        assertEquals(1, sessions.size());
        assertEquals("test-device-1", sessions.findById(verified.getAccessToken()).getDeviceId());
        assertNull(verifications.findByToken(token));
        AuthService.Rejected reused = assertThrows(AuthService.Rejected.class, () -> auth.verify(token, "test-device-1", null));
        assertEquals(AuthService.Rejected.Reason.VALIDATION, reused.getReason());
        assertTrue(passwordHasher.matches("password1", account.getPasswordHash()));
    }

    @Test
    void verifyAssignsAdminWhenUsernameIsBootstrapAdmin() {
        AuthService adminAuth = new AuthService(
            passwordHasher,
            accounts,
            profiles,
            sessions,
            events,
            verifications,
            mail,
            clock,
            BASE_URL,
            List.of("ngoc_anh")
        );
        adminAuth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        String token = verifications.findByEmail("anh@example.com").getToken();

        TokenResponse verified = adminAuth.verify(token, "test-device-1", null);

        assertEquals(identity.account.PlatformRole.ADMIN, verified.getUser().getPlatformRole());
        TokenResponse login = adminAuth.login(new LoginRequest("anh@example.com", "password1", "test-device-1"));
        assertEquals(identity.account.PlatformRole.ADMIN, login.getUser().getPlatformRole());
    }

    @Test
    void verifyRejectsWhenUsernameOrEmailAlreadyExists() {
        verifyNgocAnh();
        verifications.save(new EmailVerification(
            "token-2",
            "ngoc_anh",
            "other@example.com",
            "ngoc_anh",
            "hash",
            NOW,
            NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS)
        ));

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class, () -> auth.verify("token-2", "test-device-1", null));

        assertEquals(AuthService.Rejected.Reason.CONFLICT, rejected.getReason());
        assertEquals("token-2", verifications.findByToken("token-2").getToken());
        assertNull(accounts.findByEmail("other@example.com"));
    }

    @Test
    void verifyRejectsExpiredAndUnknownToken() {
        auth.register(new RegisterRequest("ngoc_anh", "anh@example.com", "password1"));
        String token = verifications.findByEmail("anh@example.com").getToken();
        clock.setInstant(NOW.plusSeconds(AuthService.VERIFICATION_TTL_SECONDS));

        AuthService.Rejected expired = assertThrows(AuthService.Rejected.class, () -> auth.verify(token, "test-device-1", null));
        AuthService.Rejected unknown = assertThrows(AuthService.Rejected.class, () -> auth.verify("missing", "test-device-1", null));
        AuthService.Rejected blank = assertThrows(AuthService.Rejected.class, () -> auth.verify("  ", "test-device-1", null));

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
            () -> auth.login(new LoginRequest("anh@example.com", "password1", "test-device-1")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(0, events.userIds.size());
    }

    @Test
    void loginSameDeviceRotatesSessionAndPublishesEvent() {
        Account account = verifyNgocAnh();
        Instant accountCreatedAt = account.getCreatedAt();
        events.userIds.clear();

        TokenResponse first = auth.login(new LoginRequest("anh@example.com", "password1", "test-device-1"));
        TokenResponse second = auth.login(new LoginRequest("anh@example.com", "password1", "test-device-1"));

        Session loggedInSession = sessions.findById(second.getAccessToken());
        assertNotEquals(first.getAccessToken(), second.getAccessToken());
        assertEquals(1, sessions.size());
        assertNull(sessions.findById(first.getAccessToken()));
        assertEquals(accountCreatedAt, accounts.findByUsername("ngoc_anh").getCreatedAt());
        assertFalse(loggedInSession.getCreatedAt().isBefore(accountCreatedAt));
        assertEquals(loggedInSession.getCreatedAt().plusSeconds(AuthService.SESSION_IDLE_SECONDS), loggedInSession.getExpiresAt());
        assertEquals("test-device-1", loggedInSession.getDeviceId());
        assertEquals(account.getUserId(), auth.currentUserId(second.getAccessToken()));
        assertEquals(List.of(account.getUserId(), account.getUserId()), events.userIds);
    }

    @Test
    void loginDifferentDevicesKeepSeparateSessions() {
        Account account = verifyNgocAnh();
        for (Session existing : sessions.findByUserId(account.getUserId())) {
            sessions.delete(existing.getSessionId());
        }
        TokenResponse web = auth.login(new LoginRequest("anh@example.com", "password1", "device-web-01"));
        TokenResponse mobile = auth.login(new LoginRequest("anh@example.com", "password1", "device-mobile-01"));
        assertEquals(2, sessions.size());
        assertNotNull(auth.currentUserId(web.getAccessToken()));
        assertNotNull(auth.currentUserId(mobile.getAccessToken()));
        assertEquals(web.getUser().getUserId(), mobile.getUser().getUserId());
    }

    @Test
    void loginRejectsWrongPasswordWithoutNewSession() {
        verifyNgocAnh();
        int before = sessions.size();
        int eventsBefore = events.userIds.size();

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.login(new LoginRequest("anh@example.com", "wrong-password", "test-device-1")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(eventsBefore, events.userIds.size());
    }

    @Test
    void loginRejectsUnknownUserWithoutNewSession() {
        int before = sessions.size();

        AuthService.Rejected rejected = assertThrows(AuthService.Rejected.class,
            () -> auth.login(new LoginRequest("missing@example.com", "password1", "test-device-1")));

        assertEquals(AuthService.Rejected.Reason.UNAUTHENTICATED, rejected.getReason());
        assertEquals(before, sessions.size());
        assertEquals(0, events.userIds.size());
    }

    @Test
    void loginByEmailOpensASession() {
        Account account = verifyNgocAnh();

        TokenResponse loggedIn = auth.login(new LoginRequest("anh@example.com", "password1", "test-device-1"));

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
        auth.verify(verifications.findByEmail("anh@example.com").getToken(), "test-device-1", null);
        return accounts.findByUsername("ngoc_anh");
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

        @Override
        public void updatePlatformRole(String userId, PlatformRole role) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withPlatformRole(role));
                    return;
                }
            }
        }

        @Override
        public void updateBannedAt(String userId, Instant bannedAt) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withBannedAt(bannedAt));
                    return;
                }
            }
        }

        @Override
        public void updatePasswordHash(String userId, String passwordHash) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withPasswordHash(passwordHash));
                    return;
                }
            }
        }

    }

    private static final class MemoryProfiles implements ProfileRepository {
        private final List<Profile> rows = new ArrayList<>();

        @Override
        public Profile find(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).findFirst().orElse(null);
        }

        @Override
        public void save(Profile profile) {
            rows.removeIf(row -> row.getUserId().equals(profile.getUserId()));
            rows.add(profile);
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

        @Override
        public Session findByUserIdAndDeviceId(String userId, String deviceId) {
            if (deviceId == null) {
                return null;
            }
            return rows.stream()
                .filter(row -> row.getUserId().equals(userId) && deviceId.equals(row.getDeviceId()))
                .findFirst()
                .orElse(null);
        }

        @Override
        public List<Session> findByUserId(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).toList();
        }

        @Override
        public void delete(String sessionId) {
            rows.removeIf(row -> row.getSessionId().equals(sessionId));
        }

        @Override
        public void touch(String sessionId, Instant lastSeenAt, Instant expiresAt) {
            for (int i = 0; i < rows.size(); i++) {
                Session row = rows.get(i);
                if (row.getSessionId().equals(sessionId)) {
                    rows.set(i, new Session(
                        row.getSessionId(),
                        row.getUserId(),
                        row.getCreatedAt(),
                        expiresAt,
                        row.getDeviceId(),
                        row.getDeviceLabel(),
                        row.getUserAgent(),
                        lastSeenAt
                    ));
                    return;
                }
            }
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
