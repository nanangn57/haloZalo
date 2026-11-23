package app;

import identity.event.GroupMembershipChange;

/**
 * Turns a group membership change into the short text members read in the chat.
 */
public final class GroupSystemText {
    private GroupSystemText() {
    }

    /**
     * The text for the change, or null when members should not be told: a dissolved group has no members left to read it.
     */
    public static String of(GroupMembershipChange change, String actorName, String targetName) {
        return switch (change.type()) {
            case MEMBER_ADDED -> actorName + " added " + targetName;
            case MEMBER_REMOVED -> actorName + " removed " + targetName;
            case MEMBER_LEFT -> actorName + " left the group";
            case OWNER_TRANSFERRED -> actorName + " made " + targetName + " the group owner";
            case GROUP_DISSOLVED -> null;
        };
    }
}
