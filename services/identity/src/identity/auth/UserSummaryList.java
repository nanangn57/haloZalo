package identity.auth;

import java.util.List;

/**
 * Response of GET /users/lookup. Names only: no email, since any signed-in user may ask about any id.
 */
public final class UserSummaryList {
    private final List<UserSummary> users;

    public UserSummaryList(List<UserSummary> users) {
        this.users = List.copyOf(users);
    }

    public List<UserSummary> getUsers() {
        return users;
    }

    public static final class UserSummary {
        private final String userId;
        private final String username;
        private final String displayName;

        public UserSummary(String userId, String username) {
            this(userId, username, null);
        }

        public UserSummary(String userId, String username, String displayName) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
        }

        public String getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
