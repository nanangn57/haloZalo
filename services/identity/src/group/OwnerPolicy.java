package group;

public final class OwnerPolicy implements RolePolicy {

    @Override
    public boolean alows(GroupAction action) {
        return action == GroupAction.ADD_MEMBER || action == GroupAction.REMOVE_MEMBER;
    }
}
