package identity.service;

import identity.access.PlatformAdminBootstrap;
import identity.account.Account;
import identity.account.PlatformRole;
import identity.account.Profile;
import identity.auth.LoginRequest;
import identity.auth.RegisterAccepted;
import identity.auth.RegisterRequest;
import identity.auth.TokenResponse;
import identity.event.EventPublisher;
import identity.mail.MailSender;
import identity.mail.VerificationMail;
import identity.password.PasswordHasher;
import identity.session.Session;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;
import identity.storage.SessionRepository;
import identity.storage.VerificationRepository;
import identity.verification.EmailVerification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class AuthService {
    /** Idle window returned as TokenResponse.expiresIn; session gia hạn khi còn dùng. */
    public static final int SESSION_IDLE_SECONDS = 2_592_000;
    /** Absolute max từ createdAt (90 ngày). */
    public static final int SESSION_ABSOLUTE_SECONDS = 7_776_000;
    /** Alias cũ — idle seconds (test / TokenResponse). */
    public static final int SESSION_TTL_SECONDS = SESSION_IDLE_SECONDS;
    public static final int SESSION_TOUCH_SECONDS = 300;
    public static final int VERIFICATION_TTL_SECONDS = 120;
    public static final int RESEND_MIN_INTERVAL_SECONDS = 10;
    public static final String ACTIVATION_EMAIL = "email";
    public static final String ACTIVATION_IMMEDIATE = "immediate";
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 32;
    private static final int DISPLAY_NAME_MAX = 64;
    private static final int DEVICE_ID_MIN = 8;
    private static final int DEVICE_ID_MAX = 64;
    private static final int DEVICE_LABEL_MAX = 128;
    private static final int USER_AGENT_MAX = 512;
    private static final int DERIVE_ATTEMPTS = 10000;
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final Pattern NOT_USERNAME_CHAR = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final PasswordHasher passwordHasher;
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final SessionRepository sessions;
    private final EventPublisher events;
    private final VerificationRepository verifications;
    private final MailSender mail;
    private final Clock clock;
    private final String publicBaseUrl;
    private final Set<String> platformAdminUsernames;
    private final String registrationActivation;

    /** Test helper: email activation, no platform-admin bootstrap list. */
    public AuthService(
        PasswordHasher passwordHasher,
        AccountRepository accounts,
        ProfileRepository profiles,
        SessionRepository sessions,
        EventPublisher events,
        VerificationRepository verifications,
        MailSender mail,
        Clock clock,
        String publicBaseUrl
    ) {
        this(passwordHasher, accounts, profiles, sessions, events, verifications, mail, clock, publicBaseUrl, List.of(), ACTIVATION_EMAIL);
    }

    /** Test helper: email activation with platform-admin bootstrap list. */
    public AuthService(
        PasswordHasher passwordHasher,
        AccountRepository accounts,
        ProfileRepository profiles,
        SessionRepository sessions,
        EventPublisher events,
        VerificationRepository verifications,
        MailSender mail,
        Clock clock,
        String publicBaseUrl,
        List<String> platformAdmins
    ) {
        this(passwordHasher, accounts, profiles, sessions, events, verifications, mail, clock, publicBaseUrl, platformAdmins, ACTIVATION_EMAIL);
    }

    @Autowired
    public AuthService(
        PasswordHasher passwordHasher,
        AccountRepository accounts,
        ProfileRepository profiles,
        SessionRepository sessions,
        EventPublisher events,
        VerificationRepository verifications,
        MailSender mail,
        Clock clock,
        @Value("${identity.public-base-url}") String publicBaseUrl,
        @Value("${identity.platform-admins:}") List<String> platformAdmins,
        @Value("${identity.registration.activation:email}") String registrationActivation
    ) {
        this.passwordHasher = passwordHasher;
        this.accounts = accounts;
        this.profiles = profiles;
        this.sessions = sessions;
        this.events = events;
        this.verifications = verifications;
        this.mail = mail;
        this.clock = clock;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.platformAdminUsernames = PlatformAdminBootstrap.normalize(platformAdmins);
        this.registrationActivation = normalizeActivation(registrationActivation);
    }

    public RegisterAccepted register(RegisterRequest request) {
        validateRegister(request);
        String email = request.getEmail();
        reserveEmail(email);
        String username = resolveUsername(request);
        String displayName = request.getDisplayName().trim();
        if (ACTIVATION_IMMEDIATE.equals(registrationActivation)) {
            Account created = createAccount(username, email, passwordHasher.hash(request.getPassword()), displayName);
            events.publishRegistered(created.getUserId());
            return RegisterAccepted.active(email);
        }
        Instant createdAt = clock.instant();
        EmailVerification pending = new EmailVerification(
            UUID.randomUUID().toString(),
            username,
            email,
            displayName,
            passwordHasher.hash(request.getPassword()),
            createdAt,
            createdAt.plusSeconds(VERIFICATION_TTL_SECONDS)
        );
        verifications.save(pending);
        try {
            sendVerificationMail(pending);
        } catch (RuntimeException ex) {
            verifications.delete(pending.getToken());
            log.warn("Could not send verification email to {}: {}", pending.getEmail(), ex.toString());
            throw new Rejected(Rejected.Reason.VALIDATION, "Could not send verification email");
        }
        return RegisterAccepted.pendingVerification(pending.getEmail());
    }

    /**
     * Issues a fresh link for a pending registration. A link younger than
     * {@link #RESEND_MIN_INTERVAL_SECONDS} seconds is still the live one, so asking again is refused.
     */
    public RegisterAccepted resendVerification(String email) {
        if (!validEmail(email)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid email");
        }
        EmailVerification pending = verifications.findByEmail(email);
        if (pending == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "No pending verification for this email");
        }
        Instant now = clock.instant();
        if (active(pending) && pending.getCreatedAt().plusSeconds(RESEND_MIN_INTERVAL_SECONDS).isAfter(now)) {
            throw new Rejected(Rejected.Reason.TOO_MANY_REQUESTS, "Please wait before requesting another email");
        }
        EmailVerification renewed = pending.renewed(
            UUID.randomUUID().toString(),
            now,
            now.plusSeconds(VERIFICATION_TTL_SECONDS)
        );
        verifications.delete(pending.getToken());
        verifications.save(renewed);
        try {
            sendVerificationMail(renewed);
        } catch (RuntimeException ex) {
            verifications.delete(renewed.getToken());
            verifications.save(pending);
            log.warn("Could not send verification email to {}: {}", email, ex.toString());
            throw new Rejected(Rejected.Reason.VALIDATION, "Could not send verification email");
        }
        return RegisterAccepted.pendingVerification(email);
    }

    public TokenResponse verify(String token, String deviceId, String deviceLabel) {
        if (token == null || token.isBlank()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Verification link is invalid or expired");
        }
        requireDeviceId(deviceId);
        EmailVerification pending = verifications.findByToken(token);
        if (pending == null || !pending.getExpiresAt().isAfter(clock.instant())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Verification link is invalid or expired");
        }
        if (accounts.findByUsername(pending.getUsername()) != null || accounts.findByEmail(pending.getEmail()) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        Account account = createAccount(
            pending.getUsername(),
            pending.getEmail(),
            pending.getPasswordHash(),
            pending.getDisplayName()
        );
        verifications.delete(pending.getToken());
        events.publishRegistered(account.getUserId());
        events.publishVerified(account.getUserId());
        events.publishLoggedIn(account.getUserId());
        Session session = openSession(account.getUserId(), deviceId, sanitizeLabel(deviceLabel), null);
        return new TokenResponse(account, session, SESSION_IDLE_SECONDS);
    }

    private void sendVerificationMail(EmailVerification pending) {
        VerificationMail message = VerificationMail.of(
            verificationLink(pending.getToken()),
            VERIFICATION_TTL_SECONDS
        );
        mail.send(pending.getEmail(), message.getSubject(), message.getBody());
    }

    private Account createAccount(String username, String email, String passwordHash, String displayName) {
        PlatformRole role = isBootstrapAdmin(username)
            ? PlatformRole.ADMIN
            : PlatformRole.USER;
        Account account = new Account(
            UUID.randomUUID().toString(),
            username,
            email,
            passwordHash,
            clock.instant(),
            role
        );
        accounts.save(account);
        String name = displayName == null || displayName.isBlank() ? username : displayName;
        profiles.save(new Profile(account.getUserId(), name, null, null));
        return account;
    }

    private static String normalizeActivation(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVATION_EMAIL;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (ACTIVATION_IMMEDIATE.equals(normalized) || ACTIVATION_EMAIL.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException(
            "identity.registration.activation must be 'email' or 'immediate', got: " + value
        );
    }

    private boolean isBootstrapAdmin(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return platformAdminUsernames.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public TokenResponse login(LoginRequest request) {
        validateLogin(request);
        Account account = accounts.findByEmail(request.getLogin());
        if (account == null) {
            passwordHasher.matches(request.getPassword(), DUMMY_HASH);
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Invalid login or password");
        }
        if (!passwordHasher.matches(request.getPassword(), account.getPasswordHash())) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Invalid login or password");
        }
        if (account.isBanned()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Account is banned");
        }
        Session session = openSession(
            account.getUserId(),
            request.getDeviceId().trim(),
            sanitizeLabel(request.getDeviceLabel()),
            sanitizeUserAgent(request.getUserAgent())
        );
        TokenResponse response = new TokenResponse(account, session, SESSION_IDLE_SECONDS);
        events.publishLoggedIn(account.getUserId());
        return response;
    }

    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        sessions.delete(sessionId);
    }

    /** Sessions of this user that have not expired yet, newest first. */
    public List<Session> listSessions(String userId) {
        Instant now = Instant.now();
        return sessions.findByUserId(userId).stream()
            .filter(session -> session.getExpiresAt().isAfter(now))
            .sorted(Comparator.comparing(Session::getCreatedAt).reversed())
            .toList();
    }

    public void revokeSession(String userId, String sessionId) {
        Session session = sessionId == null || sessionId.isBlank() ? null : sessions.findById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Session not found");
        }
        sessions.delete(session.getSessionId());
    }

    public String currentUserId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        Session session = sessions.findById(sessionId);
        Instant now = clock.instant();
        if (session == null || !session.getExpiresAt().isAfter(now)) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        Account account = accounts.findById(session.getUserId());
        if (account == null || account.isBanned()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        touchIfDue(session, now);
        return session.getUserId();
    }

    public Account currentAccount(String sessionId) {
        return accountFor(currentUserId(sessionId));
    }

    public Account accountFor(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        Account account = accounts.findById(userId);
        if (account == null) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return account;
    }

    /** Email must be free. A pending row that has expired is dropped so the address can register again. */
    private void reserveEmail(String email) {
        if (accounts.findByEmail(email) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        EmailVerification byEmail = verifications.findByEmail(email);
        if (active(byEmail)) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        if (byEmail != null) {
            verifications.delete(byEmail.getToken());
        }
    }

    private String resolveUsername(RegisterRequest request) {
        String requested = request.getUsername();
        if (requested != null && !requested.isEmpty()) {
            if (!usernameAvailable(requested)) {
                throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
            }
            return requested;
        }
        return deriveUsername(request.getEmail());
    }

    private String deriveUsername(String email) {
        String local = email.substring(0, email.indexOf('@'));
        String base = NOT_USERNAME_CHAR.matcher(local).replaceAll("_");
        if (base.length() > USERNAME_MAX) {
            base = base.substring(0, USERNAME_MAX);
        }
        StringBuilder padded = new StringBuilder(base);
        while (padded.length() < USERNAME_MIN) {
            padded.append('_');
        }
        base = padded.toString();
        if (usernameAvailable(base)) {
            return base;
        }
        for (int n = 1; n <= DERIVE_ATTEMPTS; n++) {
            String suffix = String.valueOf(n);
            String stem = base.length() + suffix.length() > USERNAME_MAX
                ? base.substring(0, USERNAME_MAX - suffix.length())
                : base;
            String candidate = stem + suffix;
            if (usernameAvailable(candidate)) {
                return candidate;
            }
        }
        throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
    }

    /** Free when no account and no live pending registration holds it. Expired pending rows are dropped. */
    private boolean usernameAvailable(String username) {
        if (accounts.findByUsername(username) != null) {
            return false;
        }
        EmailVerification pending = verifications.findByUsername(username);
        if (active(pending)) {
            return false;
        }
        if (pending != null) {
            verifications.delete(pending.getToken());
        }
        return true;
    }

    private boolean active(EmailVerification verification) {
        return verification != null && verification.getExpiresAt().isAfter(clock.instant());
    }

    private String verificationLink(String token) {
        return publicBaseUrl + "/auth/verify?token=" + token;
    }

    private static String stripTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private Session openSession(String userId, String deviceId, String deviceLabel, String userAgent) {
        Session existing = sessions.findByUserIdAndDeviceId(userId, deviceId);
        if (existing != null) {
            sessions.delete(existing.getSessionId());
        }
        Instant createdAt = clock.instant();
        Instant expiresAt = nextExpiry(createdAt, createdAt);
        Session session = new Session(
            UUID.randomUUID().toString(),
            userId,
            createdAt,
            expiresAt,
            deviceId,
            deviceLabel,
            userAgent,
            createdAt
        );
        sessions.save(session);
        return session;
    }

    private void touchIfDue(Session session, Instant now) {
        Instant last = session.getLastSeenAt() == null ? session.getCreatedAt() : session.getLastSeenAt();
        if (Duration.between(last, now).getSeconds() < SESSION_TOUCH_SECONDS) {
            return;
        }
        Instant expiresAt = nextExpiry(session.getCreatedAt(), now);
        if (!expiresAt.isAfter(now)) {
            sessions.delete(session.getSessionId());
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        sessions.touch(session.getSessionId(), now, expiresAt);
    }

    private static Instant nextExpiry(Instant createdAt, Instant now) {
        Instant idle = now.plusSeconds(SESSION_IDLE_SECONDS);
        Instant absolute = createdAt.plusSeconds(SESSION_ABSOLUTE_SECONDS);
        return idle.isBefore(absolute) ? idle : absolute;
    }

    private static void requireDeviceId(String deviceId) {
        if (!validDeviceId(deviceId)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "deviceId is required");
        }
    }

    private static boolean validDeviceId(String deviceId) {
        return deviceId != null && DEVICE_ID.matcher(deviceId.trim()).matches();
    }

    private static String sanitizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        return trimmed.length() <= DEVICE_LABEL_MAX ? trimmed : trimmed.substring(0, DEVICE_LABEL_MAX);
    }

    private static String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() <= USER_AGENT_MAX ? trimmed : trimmed.substring(0, USER_AGENT_MAX);
    }

    private static void validateRegister(RegisterRequest request) {
        if (request == null
            || !validEmail(request.getEmail())
            || !validPassword(request.getPassword())
            || !validDisplayName(request.getDisplayName())
            || !validOptionalUsername(request.getUsername())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid register request");
        }
    }

    private static void validateLogin(LoginRequest request) {
        if (request == null
            || request.getLogin() == null
            || request.getLogin().length() < 3
            || request.getLogin().length() > 254
            || request.getLogin().indexOf('@') < 0
            || !validPassword(request.getPassword())
            || !validDeviceId(request.getDeviceId())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid login request");
        }
        if (request.getDeviceLabel() != null && request.getDeviceLabel().length() > DEVICE_LABEL_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid login request");
        }
        if (request.getUserAgent() != null && request.getUserAgent().length() > USER_AGENT_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid login request");
        }
    }

    static boolean validEmail(String email) {
        if (email == null || email.length() > 254) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1 && email.indexOf('@', at + 1) < 0;
    }

    static boolean validPassword(String password) {
        return password != null && password.length() >= 8 && password.length() <= 72;
    }

    private static boolean validDisplayName(String displayName) {
        return displayName != null && !displayName.isBlank() && displayName.trim().length() <= DISPLAY_NAME_MAX;
    }

    private static boolean validOptionalUsername(String username) {
        return username == null || username.isEmpty() || USERNAME.matcher(username).matches();
    }

    public static final class Rejected extends RuntimeException {
        public enum Reason {
            VALIDATION,
            CONFLICT,
            UNAUTHENTICATED,
            FORBIDDEN,
            NOT_FOUND,
            TOO_MANY_REQUESTS
        }

        private final Reason reason;

        public Rejected(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
