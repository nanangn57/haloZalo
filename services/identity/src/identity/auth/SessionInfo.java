package identity.auth;

import identity.session.Session;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SessionInfo {
    private final String sessionId;
    private final String deviceId;
    private final String deviceLabel;
    private final String userAgent;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant lastSeenAt;

    public SessionInfo(
        String sessionId,
        String deviceId,
        String deviceLabel,
        String userAgent,
        Instant createdAt,
        Instant expiresAt,
        Instant lastSeenAt
    ) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.deviceLabel = deviceLabel;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastSeenAt = lastSeenAt;
    }

    public static SessionInfo from(Session session) {
        return new SessionInfo(
            session.getSessionId(),
            session.getDeviceId(),
            session.getDeviceLabel(),
            session.getUserAgent(),
            session.getCreatedAt(),
            session.getExpiresAt(),
            session.getLastSeenAt()
        );
    }

    public String getSessionId() {
        return sessionId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
