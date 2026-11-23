package service;

import conversation.ConversationFilter;
import conversation.ConversationPrefsPatch;
import conversation.ConversationSummary;
import conversation.ConversationType;
import event.RecordingEvents;
import member.ConversationMembers;
import member.StandaloneBlockChecker;
import member.StandaloneFriendshipChecker;
import member.StandaloneGroupMembers;
import message.MessageContent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;
import storage.MemoryReadMarkers;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stranger's DIRECT conversation is OPEN for the one who started it and PENDING for the other, and a block
 * stops opening and writing in either direction.
 */
class MessageRequestServiceTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000003";
    private static final ConversationFilter REQUESTS = new ConversationFilter(true, false);

    private MessageService service;
    private StandaloneFriendshipChecker friendships;
    private StandaloneBlockChecker blocks;
    private StandaloneGroupMembers groups;

    @BeforeEach
    void setUp() {
        MemoryConversations conversations = new MemoryConversations();
        MemoryMessages messages = new MemoryMessages(conversations);
        groups = new StandaloneGroupMembers();
        friendships = new StandaloneFriendshipChecker();
        blocks = new StandaloneBlockChecker();
        service = new MessageService(conversations, messages, messages, new MemoryReactions(),
            new MemoryReadMarkers(messages), conversations.prefs(), new ConversationMembers(groups), friendships, blocks,
            new RecordingEvents());
    }

    @Test
    void strangerChatIsPendingOnlyForTheRecipient() {
        MessageService.Opened opened = service.openDirect(ALICE, BOB);
        String id = opened.conversation().conversation().getConversationId();

        assertTrue(opened.created());
        assertFalse(opened.conversation().pendingRequest());
        assertFalse(service.conversation(id, ALICE).pendingRequest());
        assertTrue(service.conversation(id, BOB).pendingRequest());
    }

    @Test
    void friendsChatIsOpenForBoth() {
        friendships.befriend(ALICE, BOB);

        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();

        assertFalse(service.conversation(id, ALICE).pendingRequest());
        assertFalse(service.conversation(id, BOB).pendingRequest());
        assertEquals(List.of(id), ids(service.conversations(BOB, null, null)));
    }

    @Test
    void requestIsHiddenFromTheNormalInboxUntilAccepted() {
        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();
        send(id, ALICE, "hi stranger");

        assertEquals(List.of(), ids(service.conversations(BOB, null, null)));
        assertEquals(List.of(id), ids(service.conversations(BOB, null, null, REQUESTS)));
        assertTrue(service.conversations(BOB, null, null, REQUESTS).conversations().get(0).pendingRequest());
        // The sender sees it as an ordinary chat, and has nothing in requests.
        assertEquals(List.of(id), ids(service.conversations(ALICE, null, null)));
        assertEquals(List.of(), ids(service.conversations(ALICE, null, null, REQUESTS)));

        ConversationSummary accepted = service.acceptRequest(id, BOB);

        assertFalse(accepted.pendingRequest());
        assertEquals(List.of(id), ids(service.conversations(BOB, null, null)));
        assertEquals(List.of(), ids(service.conversations(BOB, null, null, REQUESTS)));
    }

    @Test
    void acceptTwiceIsFineAndOnlyMembersOfADirectChatCanAccept() {
        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();
        groups.replace(GROUP, Set.of(ALICE, BOB));
        service.openConversation(GROUP, ConversationType.GROUP, null);

        service.acceptRequest(id, BOB);
        assertFalse(service.acceptRequest(id, BOB).pendingRequest());
        assertFalse(service.acceptRequest(id, ALICE).pendingRequest());
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.acceptRequest(id, CAROL));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.acceptRequest(GROUP, BOB));
        assertReason(MessageService.Rejected.Reason.UNAUTHENTICATED, () -> service.acceptRequest(id, null));
    }

    @Test
    void recipientWritingBackAcceptsTheRequest() {
        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();
        send(id, ALICE, "hello");

        send(id, BOB, "who is this?");

        assertFalse(service.conversation(id, BOB).pendingRequest());
        assertEquals(List.of(id), ids(service.conversations(BOB, null, null)));
    }

    @Test
    void becomingFriendsEndsTheRequestByItself() {
        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();
        assertTrue(service.conversation(id, BOB).pendingRequest());

        friendships.befriend(ALICE, BOB);

        assertEquals(List.of(), ids(service.conversations(BOB, null, null, REQUESTS)));
        assertFalse(service.conversation(id, BOB).pendingRequest());
        assertEquals(List.of(id), ids(service.conversations(BOB, null, null)));
    }

    @Test
    void groupsNeverAppearInRequests() {
        groups.replace(GROUP, Set.of(ALICE, BOB));
        service.openConversation(GROUP, ConversationType.GROUP, null);

        assertEquals(List.of(GROUP), ids(service.conversations(BOB, null, null)));
        assertEquals(List.of(), ids(service.conversations(BOB, null, null, REQUESTS)));
    }

    @Test
    void blockedUsersCannotOpenInEitherDirection() {
        blocks.block(BOB, ALICE);

        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.openDirect(ALICE, BOB));
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.openDirect(BOB, ALICE));
        assertEquals(List.of(), ids(service.conversations(ALICE, null, null)));
    }

    @Test
    void blockAfterOpeningStopsSendAndForwardButNotReading() {
        friendships.befriend(ALICE, BOB);
        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();
        send(id, ALICE, "before the block");
        String source = service.messages(id, ALICE, null, null, null).messages().get(0).getMessageId();

        blocks.block(ALICE, BOB);

        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> send(id, ALICE, "after"));
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> send(id, BOB, "after"));
        assertReason(MessageService.Rejected.Reason.FORBIDDEN,
            () -> service.forward(id, BOB, UUID.randomUUID().toString(), source));
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.openDirect(BOB, ALICE));
        assertEquals(1, service.messages(id, BOB, null, null, null).messages().size());
    }

    @Test
    void blockDoesNotStopGroupMessages() {
        groups.replace(GROUP, Set.of(ALICE, BOB));
        service.openConversation(GROUP, ConversationType.GROUP, null);
        blocks.block(ALICE, BOB);

        send(GROUP, ALICE, "still in the group");

        assertEquals(1, service.messages(GROUP, BOB, null, null, null).messages().size());
    }

    @Test
    void pendingRecipientCanStillPinOrArchiveTheRequest() {
        String id = service.openDirect(ALICE, BOB).conversation().conversation().getConversationId();

        service.updatePrefs(id, BOB, new ConversationPrefsPatch(null, true, null));

        assertTrue(service.conversation(id, BOB).prefs().muted());
        assertTrue(service.conversation(id, BOB).pendingRequest());
    }

    @Test
    void openingAgainReturnsTheSameConversationWithoutNewPendingState() {
        MessageService.Opened first = service.openDirect(ALICE, BOB);
        MessageService.Opened again = service.openDirect(BOB, ALICE);

        assertFalse(again.created());
        assertEquals(first.conversation().conversation().getConversationId(),
            again.conversation().conversation().getConversationId());
        assertTrue(again.conversation().pendingRequest());
    }

    private void send(String conversationId, String sender, String text) {
        service.send(conversationId, sender, UUID.randomUUID().toString(), new MessageContent.Text(text), null, null);
    }

    private static List<String> ids(MessageService.ConversationPage page) {
        return page.conversations().stream().map(row -> row.conversation().getConversationId()).toList();
    }

    private static void assertReason(MessageService.Rejected.Reason reason, Runnable action) {
        assertEquals(reason, assertThrows(MessageService.Rejected.class, action::run).getReason());
    }
}
