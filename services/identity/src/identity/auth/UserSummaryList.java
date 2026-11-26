package identity.auth;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response of GET /users/lookup. Names and optional avatar; no email, since any signed-in user may ask about any id.
 */
public final class UserSummaryList {
    private final List<UserSummary> users;

    public UserSummaryList(List<UserSummary> users) {
        this.users = List.copyOf(users);
    }

    public List<UserSummary> getUsers() {
        return users;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class UserSummary {
        private final String userId;
        private final String username;
        private final String displayName;
        private final String avatar;

        public UserSummary(String userId, String username) {
            this(userId, username, null, null);
        }

        public UserSummary(String userId, String username, String displayName) {
            this(userId, username, displayName, null);
        }

        public UserSummary(String userId, String username, String displayName, String avatar) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.avatar = avatar;
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

        public String getAvatar() {
            return avatar;
        }
    }
}
