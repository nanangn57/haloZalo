package identity.group;

import org.springframework.stereotype.Component;

@Component
public final class GroupRolePolicyRegistry implements RolePolicyRegistry {
    private final RolePolicyRegistry members = new OwnerMemberPolicyRegistry();
    private final RolePolicy admin = new AdminPolicy();

    @Override
    public RolePolicy getPolicy(Role role) {
        if (role == Role.ADMIN) {
            return admin;
        }
        return members.getPolicy(role);
    }
}
