package group;

public final class MemberPolicy implements RolePolicy {

    @Override
    public boolean alows(GroupAction action) {
        return action == GroupAction.LEAVE;
    }
}
