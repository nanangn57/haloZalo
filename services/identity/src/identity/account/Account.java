package identity.account;

import java.time.Instant;

public final class Account {
    private final String userId;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;
    private final PlatformRole platformRole;

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
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.platformRole = platformRole == null ? PlatformRole.USER : platformRole;
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

    public boolean isPlatformAdmin() {
        return platformRole == PlatformRole.ADMIN;
    }

    public Account withPlatformRole(PlatformRole role) {
        return new Account(userId, username, email, passwordHash, createdAt, role);
    }
}
