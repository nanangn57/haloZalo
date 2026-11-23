package identity.group;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GroupResponse {
    private final String groupId;
    private final String name;
    private final String ownerId;
    private final Role role;
    private final String description;
    private final String avatar;

    public GroupResponse(String groupId, String name, String ownerId, Role role) {
        this(groupId, name, ownerId, role, null, null);
    }

    public GroupResponse(
        String groupId,
        String name,
        String ownerId,
        Role role,
        String description,
        String avatar
    ) {
        this.groupId = groupId;
        this.name = name;
        this.ownerId = ownerId;
        this.role = role;
        this.description = description;
        this.avatar = avatar;
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

    public String getDescription() {
        return description;
    }

    public String getAvatar() {
        return avatar;
    }
}
