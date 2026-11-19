package identity.group;

import java.time.Instant;

public final class Membership {
    private final String groupId;
    private final String UserId;
    private final Instant joinedAt;
    private final Role role;

    public Membership(String groupId, String UserId, Instant joinedAt, Role role) {
        this.groupId = groupId;
        this.UserId = UserId;
        this.joinedAt = joinedAt;
        this.role = role;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getUserId() {
        return UserId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Role getRole() {
        return role;
    }
}
