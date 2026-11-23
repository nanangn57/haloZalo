package identity.account;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProfileView {
    private final String userId;
    private final String username;
    private final String email;
    private final PlatformRole platformRole;
    private final String displayName;
    private final String avatar;
    private final String bio;

    public ProfileView(
        String userId,
        String username,
        String email,
        PlatformRole platformRole,
        String displayName,
        String avatar
    ) {
        this(userId, username, email, platformRole, displayName, avatar, null);
    }

    public ProfileView(
        String userId,
        String username,
        String email,
        PlatformRole platformRole,
        String displayName,
        String avatar,
        String bio
    ) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.platformRole = platformRole == null ? PlatformRole.USER : platformRole;
        this.displayName = displayName;
        this.avatar = avatar;
        this.bio = bio;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getBio() {
        return bio;
    }
}
