package identity.group;

import java.time.Instant;

public class Group {
    private final String groupId;
    private final String groupName;
    private final String description;
    private final Instant createdAt;
    private final String ownerId;
    private final String avatar;

    public Group(String groupId, String groupName, String description, Instant createdAt, String ownerId) {
        this(groupId, groupName, description, createdAt, ownerId, null);
    }

    public Group(
        String groupId,
        String groupName,
        String description,
        Instant createdAt,
        String ownerId,
        String avatar
    ) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
        this.createdAt = createdAt;
        this.ownerId = ownerId;
        this.avatar = avatar;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getGroupDescription() {
        return description;
    }

    public Instant getGroupCreatedAt() {
        return createdAt;
    }

    public String getGroupOwnerId() {
        return ownerId;
    }

    public String getGroupAvatar() {
        return avatar;
    }

    public Group withDetails(String name, String newDescription, String newAvatar) {
        return new Group(groupId, name, newDescription, createdAt, ownerId, newAvatar);
    }

    public Group withOwnerId(String newOwnerId) {
        return new Group(groupId, groupName, description, createdAt, newOwnerId, avatar);
    }
}
