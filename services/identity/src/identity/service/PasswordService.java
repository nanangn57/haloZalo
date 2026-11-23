package identity.service;

import identity.account.Account;
import identity.mail.MailSender;
import identity.mail.PasswordResetMail;
import identity.password.PasswordHasher;
import identity.password.PasswordReset;
import identity.session.Session;
import identity.storage.AccountRepository;
import identity.storage.PasswordResetRepository;
import identity.storage.SessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class PasswordService {
    public static final int RESET_TTL_SECONDS = 900;
    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final PasswordHasher passwordHasher;
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordResetRepository resets;
    private final MailSender mail;
    private final Clock clock;
    private final String publicBaseUrl;

    public PasswordService(
        PasswordHasher passwordHasher,
        AccountRepository accounts,
        SessionRepository sessions,
        PasswordResetRepository resets,
        MailSender mail,
        Clock clock,
        @Value("${identity.public-base-url}") String publicBaseUrl
    ) {
        this.passwordHasher = passwordHasher;
        this.accounts = accounts;
        this.sessions = sessions;
        this.resets = resets;
        this.mail = mail;
        this.clock = clock;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    /**
     * Sends a reset link when the email belongs to an account. Returns normally either way so the
     * caller cannot learn which emails are registered.
     */
    public void forgot(String email) {
        if (!AuthService.validEmail(email)) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Invalid email");
        }
        Account account = accounts.findByEmail(email);
        if (account == null) {
            return;
        }
        Instant now = clock.instant();
        PasswordReset reset = new PasswordReset(
            UUID.randomUUID().toString(),
            account.getUserId(),
            now,
            now.plusSeconds(RESET_TTL_SECONDS)
        );
        resets.deleteByUserId(account.getUserId());
        resets.save(reset);
        try {
            PasswordResetMail message = PasswordResetMail.of(resetLink(reset.getToken()), RESET_TTL_SECONDS);
            mail.send(account.getEmail(), message.getSubject(), message.getBody());
        } catch (RuntimeException ex) {
            resets.delete(reset.getToken());
            log.warn("Could not send password reset email to {}: {}", email, ex.toString());
        }
    }

    public void reset(String token, String newPassword) {
        if (!AuthService.validPassword(newPassword)) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Invalid password");
        }
        PasswordReset pending = token == null || token.isBlank() ? null : resets.findByToken(token);
        if (pending == null || !pending.getExpiresAt().isAfter(clock.instant())) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Reset link is invalid or expired");
        }
        Account account = accounts.findById(pending.getUserId());
        if (account == null) {
            resets.delete(pending.getToken());
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Reset link is invalid or expired");
        }
        accounts.updatePasswordHash(account.getUserId(), passwordHasher.hash(newPassword));
        resets.delete(pending.getToken());
        revokeSessions(account.getUserId(), null);
    }

    public void change(
        String userId,
        String currentSessionId,
        String currentPassword,
        String newPassword,
        boolean revokeOtherSessions
    ) {
        Account account = userId == null || userId.isBlank() ? null : accounts.findById(userId);
        if (account == null) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        if (currentPassword == null || !AuthService.validPassword(newPassword)) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Invalid password change request");
        }
        if (!passwordHasher.matches(currentPassword, account.getPasswordHash())) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Current password is incorrect");
        }
        accounts.updatePasswordHash(account.getUserId(), passwordHasher.hash(newPassword));
        if (revokeOtherSessions) {
            revokeSessions(account.getUserId(), currentSessionId);
        }
    }

    private void revokeSessions(String userId, String keepSessionId) {
        for (Session session : sessions.findByUserId(userId)) {
            if (!session.getSessionId().equals(keepSessionId)) {
                sessions.delete(session.getSessionId());
            }
        }
    }

    private String resetLink(String token) {
        return publicBaseUrl + "/reset-password?token=" + token;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
