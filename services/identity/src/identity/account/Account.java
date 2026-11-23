package identity.account;

import java.time.Instant;

public final class Account {
    private final String userId;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;
    private final PlatformRole platformRole;
    private final Instant bannedAt;

    public Account(String userId, String username, String email, String passwordHash, Instant createdAt) {
        this(userId, username, email, passwordHash, createdAt, PlatformRole.USER);
    }

    public Account(
        String userId,
        String username,
        String email,
        String passwordHash,
        Instant createdAt,
        PlatformRole platformRole
    ) {
        this(userId, username, email, passwordHash, createdAt, platformRole, null);
    }

    public Account(
        String userId,
        String username,
        String email,
        String passwordHash,
        Instant createdAt,
        PlatformRole platformRole,
        Instant bannedAt
    ) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.platformRole = platformRole == null ? PlatformRole.USER : platformRole;
        this.bannedAt = bannedAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }

    public Instant getBannedAt() {
        return bannedAt;
    }

    public boolean isBanned() {
        return bannedAt != null;
    }

    public boolean isPlatformAdmin() {
        return platformRole == PlatformRole.ADMIN;
    }

    public Account withPlatformRole(PlatformRole role) {
        return new Account(userId, username, email, passwordHash, createdAt, role, bannedAt);
    }

    public Account withBannedAt(Instant banned) {
        return new Account(userId, username, email, passwordHash, createdAt, platformRole, banned);
    }

    public Account withPasswordHash(String hash) {
        return new Account(userId, username, email, hash, createdAt, platformRole, bannedAt);
    }
}
