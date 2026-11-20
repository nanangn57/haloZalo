package identity.group;

public final class OwnerMemberPolicyRegistry implements RolePolicyRegistry {
    private final RolePolicy owner = new OwnerPolicy();
    private final RolePolicy member = new MemberPolicy();

    @Override
    public RolePolicy getPolicy(Role role) {
        return switch (role) {
            case OWNER -> owner;
            case MEMBER -> member;
            case ADMIN -> throw new IllegalArgumentException("Unknown role");
        };
    }
}
