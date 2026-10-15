package group;

public final class MembershipResponse {
    private final String groupId;
    private final String userId;
    private final Role role;

    public MembershipResponse(String groupId, String userId, Role role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }
}
