package identity.group;

public interface RolePolicy {
    boolean alows(GroupAction action);
}
