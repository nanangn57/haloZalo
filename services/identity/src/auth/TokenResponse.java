package auth;

import account.Account;
import session.Session;

public final class TokenResponse {
    private final String accessToken;
    private final String tokenType;
    private final int expiresIn;
    private final User user;

    public TokenResponse(Account account, Session session, int expiresIn) {
        this.accessToken = session.getSessionId();
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = new User(account.getUserId(), account.getUsername(), account.getEmail());
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

        public User(String userId, String username, String email) {
            this.userId = userId;
            this.username = username;
            this.email = email;
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
    }
}