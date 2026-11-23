package identity.account;

public final class Profile {
    private final String userId;
    private final String displayName;
    private final String avatar;
    private final String bio;

    public Profile(String userId, String displayName, String avatar) {
        this(userId, displayName, avatar, null);
    }

    public Profile(String userId, String displayName, String avatar, String bio) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatar = avatar;
        this.bio = bio;
    }

    public String getUserId() {
        return userId;
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
