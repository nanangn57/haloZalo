package identity.access;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MembershipRequestAuthorizerTest {
    private final Set<String> groups = new HashSet<>();
    private final Set<String> chats = new HashSet<>();
    private final MembershipRequestAuthorizer authorizer = new MembershipRequestAuthorizer(
        (groupId, userId) -> groups.contains(groupId + ":" + userId),
        (userId, conversationId) -> chats.contains(conversationId + ":" + userId)
    );

    @Test
    void listAndDirectDoNotNeedAMembership() {
        assertDoesNotThrow(() -> authorizer.authorize("user-1", "/groups"));
        assertDoesNotThrow(() -> authorizer.authorize("user-1", "/conversations"));
        assertDoesNotThrow(() -> authorizer.authorize("user-1", "/conversations/direct"));
        assertNullConversation("/conversations/direct");
    }

    @Test
    void outsiderIsRejectedForGroupAndChat() {
        RequestAuthorizer.Forbidden group = assertThrows(RequestAuthorizer.Forbidden.class,
            () -> authorizer.authorize("user-1", "/groups/11111111-2222-4333-8444-555555555555/members"));
        RequestAuthorizer.Forbidden chat = assertThrows(RequestAuthorizer.Forbidden.class,
            () -> authorizer.authorize("user-1", "/conversations/99999999-8888-4777-8666-555555555555/messages"));

        assertEquals("Not allowed to change this group", group.getMessage());
        assertEquals("Not a member of this conversation", chat.getMessage());
    }

    @Test
    void memberCanEnterWithEitherUuidCase() {
        groups.add("11111111-2222-4333-8444-555555555555:user-1");

        assertDoesNotThrow(() -> authorizer.authorize(
            "user-1",
            "/groups/11111111-2222-4333-8444-555555555555/members"
        ));
        assertDoesNotThrow(() -> authorizer.authorize(
            "user-1",
            "/groups/" + "11111111-2222-4333-8444-555555555555".toUpperCase() + "/members"
        ));
    }

    private static void assertNullConversation(String path) {
        org.junit.jupiter.api.Assertions.assertNull(MembershipRequestAuthorizer.conversationId(path));
    }
}
