package identity.group;

import java.time.Instant;

public class Group {
    private final String groupId;
    private final String groupName;
    private final String description;
    private final Instant createdAt;
    private final String ownerId;

    public Group(String groupId, String groupName, String description, Instant createdAt, String ownerId) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
        this.createdAt = createdAt;
        this.ownerId = ownerId;
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
}
