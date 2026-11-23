package identity.event;

/**
 * A change to who is in a group, after it is stored. actorId is who did it. targetId is who it happened to:
 * the added, removed or leaving member, or the new owner. It is null when the change is about the whole group.
 */
public record GroupMembershipChange(Type type, String groupId, String actorId, String targetId) {
    public enum Type {
        MEMBER_ADDED,
        MEMBER_REMOVED,
        MEMBER_LEFT,
        OWNER_TRANSFERRED,
        GROUP_DISSOLVED
    }
}
