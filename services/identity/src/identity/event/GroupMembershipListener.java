package identity.event;

/**
 * Other modules in the same process implement this to react to group membership changes. A listener that
 * throws does not undo the change and does not stop the other listeners.
 */
public interface GroupMembershipListener {
    void onChange(GroupMembershipChange change);
}
