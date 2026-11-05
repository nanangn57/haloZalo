package identity.access;

import identity.group.MembershipLookup;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipConversationAccessTest {
    @Test
    void groupMembershipAllowsTheConversationThatUsesTheGroupId() {
        MembershipConversationAccess access = new MembershipConversationAccess(
            lookup((groupId, userId) -> "group-1".equals(groupId) && "user-1".equals(userId)),
            (DirectConversationAccess) null
        );

        assertTrue(access.canEnter("user-1", "group-1"));
        assertFalse(access.canEnter("user-2", "group-1"));
    }

    @Test
    void directMembershipIsAskedWhenTheIdIsNotAGroup() {
        MembershipConversationAccess access = new MembershipConversationAccess(
            lookup((groupId, userId) -> false),
            (conversationId, userId) -> "direct-1".equals(conversationId) && "user-1".equals(userId)
        );

        assertTrue(access.canEnter("user-1", "direct-1"));
        assertFalse(access.canEnter("user-2", "direct-1"));
    }

    private static MembershipLookup lookup(MemberCheck check) {
        return new MembershipLookup() {
            @Override
            public boolean isMember(String groupId, String userId) {
                return check.member(groupId, userId);
            }

            @Override
            public Set<String> memberIds(String groupId) {
                return Set.of();
            }

            @Override
            public Set<String> groupIdsOf(String userId) {
                return Set.of();
            }
        };
    }

    private interface MemberCheck {
        boolean member(String groupId, String userId);
    }
}
