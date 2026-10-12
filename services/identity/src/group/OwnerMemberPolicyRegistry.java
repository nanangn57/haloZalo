package group;

import org.springframework.stereotype.Component;

@Component
public final class OwnerMemberPolicyRegistry implements RolePolicyRegistry{
    private final RolePolicy owner = new OwnerPolicy();
    private final RolePolicy member = new MemberPolicy();

    @Override
    public RolePolicy getPolicy(Role role) {
        return switch (role) {
            case OWNER -> owner;
            case MEMBER -> member;
            default -> throw new IllegalArgumentException("Unknown role");
        };
    }
}
