package identity.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerMemberPolicyRegistryTest {
    private final OwnerMemberPolicyRegistry registry = new OwnerMemberPolicyRegistry();

    @Test
    void ownerMayManageGroupButNotLeave() {
        RolePolicy policy = registry.getPolicy(Role.OWNER);

        assertTrue(policy.alows(GroupAction.ADD_MEMBER));
        assertTrue(policy.alows(GroupAction.REMOVE_MEMBER));
        assertTrue(policy.alows(GroupAction.TRANSFER_OWNER));
        assertTrue(policy.alows(GroupAction.DISSOLVE));
        assertTrue(policy.alows(GroupAction.UPDATE_GROUP));
        assertFalse(policy.alows(GroupAction.LEAVE));
        assertSame(policy, registry.getPolicy(Role.OWNER));
    }

    @Test
    void memberMayOnlyLeave() {
        RolePolicy policy = registry.getPolicy(Role.MEMBER);

        assertTrue(policy.alows(GroupAction.LEAVE));
        assertFalse(policy.alows(GroupAction.ADD_MEMBER));
        assertFalse(policy.alows(GroupAction.REMOVE_MEMBER));
        assertFalse(policy.alows(GroupAction.TRANSFER_OWNER));
        assertFalse(policy.alows(GroupAction.DISSOLVE));
        assertFalse(policy.alows(GroupAction.UPDATE_GROUP));
    }

    @Test
    void groupRegistryServesTheSameTwoRoles() {
        GroupRolePolicyRegistry groupRegistry = new GroupRolePolicyRegistry();

        assertTrue(groupRegistry.getPolicy(Role.OWNER).alows(GroupAction.DISSOLVE));
        assertTrue(groupRegistry.getPolicy(Role.MEMBER).alows(GroupAction.LEAVE));
    }

    @Test
    void missingRoleIsRejected() {
        assertThrows(NullPointerException.class, () -> registry.getPolicy(null));
    }
}
