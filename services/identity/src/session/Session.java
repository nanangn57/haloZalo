package session;

import java.time.Instant;

public final class Session {
    private final String sessionId;
    private final String userId;
    private final Instant createdAt;
    private final Instant expiresAt;

    public Session(String sessionId, String userId, Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
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
