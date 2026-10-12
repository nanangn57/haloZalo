package account;

import java.time.Instant;

public final class Account {
    private final String userId;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;

    public Account(String userId, String username, String email, String passwordHash, Instant createdAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
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
}
