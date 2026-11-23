package identity.session;

import java.time.Instant;

public final class Session {
    private final String sessionId;
    private final String userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String deviceId;
    private final String deviceLabel;
    private final String userAgent;
    private final Instant lastSeenAt;

    /** Session test / legacy không gắn device. */
    public Session(String sessionId, String userId, Instant createdAt, Instant expiresAt) {
        this(sessionId, userId, createdAt, expiresAt, null, null, null, null);
    }

    public Session(
        String sessionId,
        String userId,
        Instant createdAt,
        Instant expiresAt,
        String deviceId,
        String deviceLabel,
        String userAgent,
        Instant lastSeenAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.deviceId = deviceId;
        this.deviceLabel = deviceLabel;
        this.userAgent = userAgent;
        this.lastSeenAt = lastSeenAt;
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

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
