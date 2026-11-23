package identity.verification;

import java.time.Instant;

public final class EmailVerification {
    private final String token;
    private final String username;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Instant createdAt;
    private final Instant expiresAt;

    public EmailVerification(
        String token,
        String username,
        String email,
        String displayName,
        String passwordHash,
        Instant createdAt,
        Instant expiresAt
    ) {
        this.token = token;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public EmailVerification renewed(String newToken, Instant newCreatedAt, Instant newExpiresAt) {
        return new EmailVerification(newToken, username, email, displayName, passwordHash, newCreatedAt, newExpiresAt);
    }
}
