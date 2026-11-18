package identity.account;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProfileView {
    private final String userId;
    private final String username;
    private final String email;
    private final String displayName;
    private final String avatar;

    public ProfileView(String userId, String username, String email, String displayName, String avatar) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.avatar = avatar;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatar() {
        return avatar;
    }
}
