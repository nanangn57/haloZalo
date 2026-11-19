package identity.group;

public final class GroupMemberResponse {
    private final String userId;
    private final String username;
    private final Role role;

    public GroupMemberResponse(String userId, String username, Role role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }
}
