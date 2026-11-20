package identity.auth;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.session.Session;

public final class TokenResponse {
    private final String accessToken;
    private final String tokenType;
    private final int expiresIn;
    private final User user;

    public TokenResponse(Account account, Session session, int expiresIn) {
        this.accessToken = session.getSessionId();
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = User.from(account);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public User getUser() {
        return user;
    }

    public static final class User {
        private final String userId;
        private final String username;
        private final String email;
        private final PlatformRole platformRole;

        public User(String userId, String username, String email) {
            this(userId, username, email, PlatformRole.USER);
        }

        public User(String userId, String username, String email, PlatformRole platformRole) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.platformRole = platformRole == null ? PlatformRole.USER : platformRole;
        }

        public static User from(Account account) {
            return new User(
                account.getUserId(),
                account.getUsername(),
                account.getEmail(),
                account.getPlatformRole()
            );
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

        public PlatformRole getPlatformRole() {
            return platformRole;
        }
    }
}
