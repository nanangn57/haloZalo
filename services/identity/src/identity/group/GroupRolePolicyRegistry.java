package identity.group;

import org.springframework.stereotype.Component;

@Component
public final class GroupRolePolicyRegistry implements RolePolicyRegistry {
    private final RolePolicyRegistry roles = new OwnerMemberPolicyRegistry();

    @Override
    public RolePolicy getPolicy(Role role) {
        return roles.getPolicy(role);
    }
}
