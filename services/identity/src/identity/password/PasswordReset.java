package identity.password;

import java.time.Instant;

public final class PasswordReset {
    private final String token;
    private final String userId;
    private final Instant createdAt;
    private final Instant expiresAt;

    public PasswordReset(String token, String userId, Instant createdAt, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
