package service;

import account.Account;
import auth.LoginRequest;
import auth.RegisterRequest;
import auth.TokenResponse;
import event.EventPublisher;
import password.PasswordHasher;
import session.Session;
import storage.AccountRepository;
import storage.SessionRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public final class AuthService {
    public static final int SESSION_TTL_SECONDS = 259200;
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final PasswordHasher passwordHasher;
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final EventPublisher events;

    public AuthService(PasswordHasher passwordHasher, AccountRepository accounts, SessionRepository sessions, EventPublisher events) {
        this.passwordHasher = passwordHasher;
        this.accounts = accounts;
        this.sessions = sessions;
        this.events = events;
    }

    public TokenResponse register(RegisterRequest request) {
        validateRegister(request);
        if (accounts.findByUsername(request.getUsername()) != null || accounts.findByEmail(request.getEmail()) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Username or email already exists");
        }
        Account account = new Account(
            UUID.randomUUID().toString(),
            request.getUsername(),
            request.getEmail(),
            passwordHasher.hash(request.getPassword())
        );
        accounts.save(account);
        return new TokenResponse(account, openSession(account.getUserId()), SESSION_TTL_SECONDS);
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
        Account account = accounts.findById(currentUserId(sessionId));
        if (account == null) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return account;
    }

    private Session openSession(String userId) {
        Session session = new Session(
            UUID.randomUUID().toString(),
            userId,
            Instant.now().plusSeconds(SESSION_TTL_SECONDS)
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
