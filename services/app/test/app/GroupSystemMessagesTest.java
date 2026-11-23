package app;

import identity.event.GroupMembershipChange;
import identity.event.LoggingEventPublisher;
import message.Message;
import service.SystemMessagePoster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupSystemMessagesTest {
    private static final String GROUP = "11111111-2222-4333-8444-555555555555";
    private static final String ALICE = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String BOB = "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    private final List<String> posted = new ArrayList<>();
    private GroupSystemMessages listener;
    private LoggingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        SystemMessagePoster poster = (conversationId, text) -> {
            posted.add(conversationId + " " + text);
            return null;
        };
        UserNames names = userId -> Map.of(ALICE, "Alice", BOB, "Bob").getOrDefault(userId, "Someone");
        listener = new GroupSystemMessages(poster, names);
        // The real publisher in front of the listener: the same path a group change takes in the app.
        publisher = new LoggingEventPublisher(envelope -> { }, List.of(listener));
    }

    @Test
    void addingAMemberPostsAliceAddedBobInTheGroupConversation() {
        publisher.publishMemberAdded(GROUP, ALICE, BOB);

        assertEquals(List.of(GROUP + " Alice added Bob"), posted);
    }

    @Test
    void removingLeavingAndTransferringEachPostTheirOwnText() {
        publisher.publishMemberRemoved(GROUP, ALICE, BOB);
        publisher.publishMemberLeft(GROUP, BOB);
        publisher.publishOwnerTransferred(GROUP, ALICE, BOB);

        assertEquals(List.of(
            GROUP + " Alice removed Bob",
            GROUP + " Bob left the group",
            GROUP + " Alice made Bob the group owner"), posted);
    }

    @Test
    void aDissolvedGroupPostsNothingBecauseNoMemberIsLeftToRead() {
        publisher.publishGroupDissolved(GROUP, ALICE);

        assertEquals(List.of(), posted);
    }

    @Test
    void anUnknownUserIsNamedSomeone() {
        publisher.publishMemberAdded(GROUP, ALICE, "cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        assertEquals(List.of(GROUP + " Alice added Someone"), posted);
    }

    @Test
    void aPosterThatFailsDoesNotFailThePublisher() {
        SystemMessagePoster failing = (conversationId, text) -> {
            throw new IllegalStateException("db down");
        };
        LoggingEventPublisher broken = new LoggingEventPublisher(envelope -> { },
            List.of(new GroupSystemMessages(failing, userId -> "X")));

        broken.publishMemberAdded(GROUP, ALICE, BOB);
    }

    @Test
    void textIsNullOnlyForDissolved() {
        assertNull(GroupSystemText.of(
            new GroupMembershipChange(GroupMembershipChange.Type.GROUP_DISSOLVED, GROUP, ALICE, null), "Alice", null));
        assertEquals("Alice added Bob", GroupSystemText.of(
            new GroupMembershipChange(GroupMembershipChange.Type.MEMBER_ADDED, GROUP, ALICE, BOB), "Alice", "Bob"));
    }
}
