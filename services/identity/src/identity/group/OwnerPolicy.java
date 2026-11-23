package identity.group;

public final class OwnerPolicy implements RolePolicy {

    @Override
    public boolean alows(GroupAction action) {
        return action == GroupAction.ADD_MEMBER
            || action == GroupAction.REMOVE_MEMBER
            || action == GroupAction.TRANSFER_OWNER
            || action == GroupAction.DISSOLVE
            || action == GroupAction.UPDATE_GROUP;
    }
}
