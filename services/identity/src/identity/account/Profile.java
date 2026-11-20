package identity.account;

public final class Profile {
    private final String userId;
    private final String displayName;
    private final String avatar;

    public Profile(String userId, String displayName, String avatar) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatar = avatar;
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
}
