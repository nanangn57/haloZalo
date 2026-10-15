package service;

import account.Account;
import auth.LoginRequest;
import auth.RegisterAccepted;
import auth.RegisterRequest;
import auth.TokenResponse;
import event.EventPublisher;
import mail.MailSender;
import password.PasswordHasher;
import session.Session;
import storage.AccountRepository;
import storage.SessionRepository;
import storage.VerificationRepository;
import verification.EmailVerification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class AuthService {
    public static final int SESSION_TTL_SECONDS = 259200;
    public static final int VERIFICATION_TTL_SECONDS = 120;
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String VERIFY_SUBJECT = "Verify your email";

    private final PasswordHasher passwordHasher;
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final EventPublisher events;
    private final VerificationRepository verifications;
    private final MailSender mail;
    private final Clock clock;
    private final String publicBaseUrl;

    public AuthService(
        PasswordHasher passwordHasher,
        AccountRepository accounts,
        SessionRepository sessions,
        EventPublisher events,
        VerificationRepository verifications,
        MailSender mail,
        Clock clock,
        @Value("${identity.public-base-url}") String publicBaseUrl
    ) {
        this.passwordHasher = passwordHasher;
        this.accounts = accounts;
        this.sessions = sessions;
        this.events = events;
        this.verifications = verifications;
        this.mail = mail;
        this.clock = clock;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    public RegisterAccepted register(RegisterRequest request) {
        validateRegister(request);
        reserve(request.getUsername(), request.getEmail());
        Instant createdAt = clock.instant();
        EmailVerification pending = new EmailVerification(
            UUID.randomUUID().toString(),
            request.getUsername(),
            request.getEmail(),
            passwordHasher.hash(request.getPassword()),
            createdAt,
            createdAt.plusSeconds(VERIFICATION_TTL_SECONDS)
        );
        verifications.save(pending);
        try {
            mail.send(pending.getEmail(), VERIFY_SUBJECT, verificationLink(pending.getToken()));
        } catch (RuntimeException ex) {
            verifications.delete(pending.getToken());
            throw ex;
        }
        return new RegisterAccepted(pending.getEmail());
    }

    public Account verify(String token) {
        if (token == null || token.isBlank()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Verification link is invalid or expired");
        }
        EmailVerification pending = verifications.findByToken(token);
        if (pending == null || !pending.getExpiresAt().isAfter(clock.instant())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Verification link is invalid or expired");
        }
        if (accounts.findByUsername(pending.getUsername()) != null || accounts.findByEmail(pending.getEmail()) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        Account account = new Account(
            UUID.randomUUID().toString(),
            pending.getUsername(),
            pending.getEmail(),
            pending.getPasswordHash(),
            clock.instant()
        );
        accounts.save(account);
        verifications.delete(pending.getToken());
        return account;
    }

    public TokenResponse login(LoginRequest request) {
        validateLogin(request);
        Account account = findAccount(request.getLogin());
        if (account == null) {
            passwordHasher.matches(request.getPassword(), DUMMY_HASH);
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Invalid login or password");
        }
        if (!passwordHasher.matches(request.getPassword(), account.getPasswordHash())) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Invalid login or password");
        }
        TokenResponse response = new TokenResponse(account, openSession(account.getUserId()), SESSION_TTL_SECONDS);
        events.publishLoggedIn(account.getUserId());
        return response;
    }

    public String currentUserId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        Session session = sessions.findById(sessionId);
        if (session == null || !session.getExpiresAt().isAfter(Instant.now())) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
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

    private void reserve(String username, String email) {
        if (accounts.findByUsername(username) != null || accounts.findByEmail(email) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        EmailVerification byUsername = verifications.findByUsername(username);
        EmailVerification byEmail = verifications.findByEmail(email);
        if (active(byUsername) || active(byEmail)) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        if (byUsername != null) {
            verifications.delete(byUsername.getToken());
        }
        if (byEmail != null && (byUsername == null || !byEmail.getToken().equals(byUsername.getToken()))) {
            verifications.delete(byEmail.getToken());
        }
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

    private Session openSession(String userId) {
        Instant createdAt = Instant.now();
        Session session = new Session(
            UUID.randomUUID().toString(),
            userId,
            createdAt,
            createdAt.plusSeconds(SESSION_TTL_SECONDS)
        );
        sessions.save(session);
        return session;
    }

    private Account findAccount(String login) {
        if (login.indexOf('@') >= 0) {
            return accounts.findByEmail(login);
        }
        return accounts.findByUsername(login);
    }

    private static void validateRegister(RegisterRequest request) {
        if (request == null
            || !USERNAME.matcher(nullToEmpty(request.getUsername())).matches()
            || !validEmail(request.getEmail())
            || !validPassword(request.getPassword())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid register request");
        }
    }

    private static void validateLogin(LoginRequest request) {
        if (request == null
            || request.getLogin() == null
            || request.getLogin().length() < 3
            || request.getLogin().length() > 254
            || !validPassword(request.getPassword())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid login request");
        }
    }

    private static boolean validEmail(String email) {
        if (email == null || email.length() > 254) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1 && email.indexOf('@', at + 1) < 0;
    }

    private static boolean validPassword(String password) {
        return password != null && password.length() >= 8 && password.length() <= 72;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static final class Rejected extends RuntimeException {
        public enum Reason {
            VALIDATION,
            CONFLICT,
            UNAUTHENTICATED
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
