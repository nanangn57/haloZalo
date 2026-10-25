package identity.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerMemberPolicyRegistryTest {
    private final OwnerMemberPolicyRegistry registry = new OwnerMemberPolicyRegistry();

    @Test
    void ownerMayAddAndRemoveButNotLeave() {
        RolePolicy policy = registry.getPolicy(Role.OWNER);

        assertTrue(policy.alows(GroupAction.ADD_MEMBER));
        assertTrue(policy.alows(GroupAction.REMOVE_MEMBER));
        assertFalse(policy.alows(GroupAction.LEAVE));
        assertSame(policy, registry.getPolicy(Role.OWNER));
    }

    @Test
    void memberMayOnlyLeave() {
        RolePolicy policy = registry.getPolicy(Role.MEMBER);

        assertTrue(policy.alows(GroupAction.LEAVE));
        assertFalse(policy.alows(GroupAction.ADD_MEMBER));
        assertFalse(policy.alows(GroupAction.REMOVE_MEMBER));
    }

    @Test
    void missingRoleIsRejected() {
        assertThrows(NullPointerException.class, () -> registry.getPolicy(null));
    }
}
