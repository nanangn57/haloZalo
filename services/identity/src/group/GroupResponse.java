package group;

public final class GroupResponse {
    private final String groupId;
    private final String name;
    private final String ownerId;
    private final Role role;

    public GroupResponse(String groupId, String name, String ownerId, Role role) {
        this.groupId = groupId;
        this.name = name;
        this.ownerId = ownerId;
        this.role = role;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Role getRole() {
        return role;
    }
}
