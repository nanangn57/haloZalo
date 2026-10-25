package identity.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipConversationAccessTest {
    @Test
    void groupMembershipAllowsTheConversationThatUsesTheGroupId() {
        MembershipConversationAccess access = new MembershipConversationAccess(
            (groupId, userId) -> "group-1".equals(groupId) && "user-1".equals(userId),
            (DirectConversationAccess) null
        );

        assertTrue(access.canEnter("user-1", "group-1"));
        assertFalse(access.canEnter("user-2", "group-1"));
    }

    @Test
    void directMembershipIsAskedWhenTheIdIsNotAGroup() {
        MembershipConversationAccess access = new MembershipConversationAccess(
            (groupId, userId) -> false,
            (conversationId, userId) -> "direct-1".equals(conversationId) && "user-1".equals(userId)
        );

        assertTrue(access.canEnter("user-1", "direct-1"));
        assertFalse(access.canEnter("user-2", "direct-1"));
    }
}
