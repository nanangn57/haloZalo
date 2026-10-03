package service;

import conversation.ConversationType;
import event.RecordingEvents;
import message.Message;
import message.MessageContent;
import message.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String DIRECT = "cccccccc-0000-4000-8000-000000000001";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000002";

    private MemoryConversations conversations;
    private MemoryMessages messages;
    private RecordingEvents events;
    private MessageService service;

    @BeforeEach
    void setUp() {
        events = new RecordingEvents();
        conversations = new MemoryConversations();
        messages = new MemoryMessages(conversations);
        service = new MessageService(conversations, messages, new MemoryReactions(), events);
        service.openConversation(DIRECT, ConversationType.DIRECT, List.of(ALICE, BOB));
        service.openConversation(GROUP, ConversationType.GROUP, List.of(ALICE, BOB, CAROL));
    }

    @Test
    void directNeedsTwoMembersAndGroupNeedsThree() {
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.openConversation(null, ConversationType.DIRECT, List.of(ALICE, BOB, CAROL)));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.openConversation(null, ConversationType.GROUP, List.of(ALICE, BOB)));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.openConversation(null, ConversationType.DIRECT, List.of(ALICE, ALICE)));
        assertReason(MessageService.Rejected.Reason.CONFLICT,
            () -> service.openConversation(DIRECT, ConversationType.DIRECT, List.of(ALICE, BOB)));
    }

    @Test
    void sendAssignsSeqPerConversationAndPublishesAfterStoring() {
        Message first = text(DIRECT, ALICE, "hi").getMessage();
        Message second = text(DIRECT, BOB, "hello").getMessage();
        Message inGroup = text(GROUP, CAROL, "hey").getMessage();

        assertEquals(1, first.getSeq());
        assertEquals(2, second.getSeq());
        assertEquals(1, inGroup.getSeq());
        assertEquals(MessageStatus.SENT, first.getStatus());
        assertEquals(List.of(first, second, inGroup), events.created);
    }

    @Test
    void retryWithSameClientMsgIdReturnsStoredMessageWithoutNewSeqOrEvent() {
        String clientMsgId = UUID.randomUUID().toString();
        MessageService.Sent first = service.send(DIRECT, ALICE, clientMsgId, new MessageContent.Text("hi"), null, null);
        MessageService.Sent retry = service.send(DIRECT, ALICE, clientMsgId, new MessageContent.Text("hi"), null, null);
        Message next = text(DIRECT, ALICE, "next").getMessage();

        assertTrue(first.isCreated());
        assertFalse(retry.isCreated());
        assertEquals(first.getMessage().getMessageId(), retry.getMessage().getMessageId());
        assertEquals(2, next.getSeq());
        assertEquals(2, events.created.size());
    }

    @Test
    void nonMemberAndUnknownConversationAreRejectedTheSameWay() {
        String outsider = "aaaaaaaa-0000-4000-8000-000000000009";
        MessageService.Rejected notMember = assertThrows(MessageService.Rejected.class, () -> text(DIRECT, CAROL, "hi"));
        MessageService.Rejected unknown = assertThrows(MessageService.Rejected.class,
            () -> text(UUID.randomUUID().toString(), outsider, "hi"));

        assertEquals(MessageService.Rejected.Reason.FORBIDDEN, notMember.getReason());
        assertEquals(notMember.getMessage(), unknown.getMessage());
        assertEquals(0, messages.size());
    }

    @Test
    void invalidContentIsRejectedBeforeAnythingIsStored() {
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.send(DIRECT, ALICE, UUID.randomUUID().toString(), new MessageContent.Emotion("heart"), null, null));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.send(DIRECT, ALICE, "not-a-uuid", new MessageContent.Text("hi"), null, null));
        assertEquals(0, messages.size());
    }

    @Test
    void replyMustPointAtAMessageInTheSameConversation() {
        Message original = text(DIRECT, ALICE, "question").getMessage();
        Message reply = service.send(DIRECT, BOB, UUID.randomUUID().toString(),
            new MessageContent.Text("answer"), null, original.getMessageId()).getMessage();

        assertEquals(original.getMessageId(), reply.getReplyTo());
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.send(GROUP, BOB, UUID.randomUUID().toString(),
            new MessageContent.Text("answer"), null, original.getMessageId()));
    }

    @Test
    void forwardKeepsItsOwnIdentityAndPointsAtTheFirstOriginal() {
        Message original = text(DIRECT, ALICE, "news").getMessage();
        Message forwarded = service.forward(GROUP, BOB, UUID.randomUUID().toString(), original.getMessageId()).getMessage();
        Message forwardedAgain = service.forward(DIRECT, BOB, UUID.randomUUID().toString(), forwarded.getMessageId()).getMessage();

        assertEquals(original.getMessageId(), forwarded.getForwardedFrom());
        assertEquals(GROUP, forwarded.getConversationId());
        assertEquals(BOB, forwarded.getSenderId());
        assertEquals(original.getContent(), forwarded.getContent());
        assertEquals(original.getMessageId(), forwardedAgain.getForwardedFrom());
    }

    @Test
    void cannotForwardAMessageFromAConversationTheSenderIsNotIn() {
        Message original = text(DIRECT, ALICE, "secret").getMessage();

        assertReason(MessageService.Rejected.Reason.FORBIDDEN,
            () -> service.forward(GROUP, CAROL, UUID.randomUUID().toString(), original.getMessageId()));
    }

    @Test
    void deleteIsSoftAndOnlyForTheSender() {
        Message original = text(DIRECT, ALICE, "oops").getMessage();
        Message reply = service.send(DIRECT, BOB, UUID.randomUUID().toString(),
            new MessageContent.Text("what?"), null, original.getMessageId()).getMessage();

        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.delete(DIRECT, original.getMessageId(), BOB));
        assertReason(MessageService.Rejected.Reason.NOT_FOUND, () -> service.delete(GROUP, original.getMessageId(), ALICE));
        Message deleted = service.delete(DIRECT, original.getMessageId(), ALICE);
        service.delete(DIRECT, original.getMessageId(), ALICE);

        assertEquals(MessageStatus.DELETED, deleted.getStatus());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(deleted.getDeletedAt(), deleted.getUpdatedAt());
        assertEquals(MessageStatus.DELETED, messages.findById(original.getMessageId()).getStatus());
        assertEquals(original.getMessageId(), messages.findById(reply.getMessageId()).getReplyTo());
        assertEquals(List.of(deleted), events.deleted);
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.forward(GROUP, BOB, UUID.randomUUID().toString(), original.getMessageId()));
    }

    @Test
    void reactionsAreOnePerUserAndCodeAndOnlyForMembers() {
        Message message = text(GROUP, ALICE, "party").getMessage();

        service.react(GROUP, message.getMessageId(), BOB, "love");
        service.react(GROUP, message.getMessageId(), BOB, "love");
        service.react(GROUP, message.getMessageId(), CAROL, "love");
        service.react(GROUP, message.getMessageId(), CAROL, "haha");
        assertEquals(3, service.reactions(GROUP, message.getMessageId(), ALICE).size());

        service.unreact(GROUP, message.getMessageId(), CAROL, "haha");
        service.unreact(GROUP, message.getMessageId(), CAROL, "haha");
        assertEquals(2, service.reactions(GROUP, message.getMessageId(), ALICE).size());
        String id = message.getMessageId();
        assertEquals(List.of(
            "added " + GROUP + " " + id + " " + BOB + " love",
            "added " + GROUP + " " + id + " " + CAROL + " love",
            "added " + GROUP + " " + id + " " + CAROL + " haha",
            "removed " + GROUP + " " + id + " " + CAROL + " haha"
        ), events.reactions);

        Message direct = text(DIRECT, ALICE, "private").getMessage();
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.react(DIRECT, direct.getMessageId(), CAROL, "like"));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.react(GROUP, message.getMessageId(), BOB, "heart"));
        assertReason(MessageService.Rejected.Reason.NOT_FOUND, () -> service.react(GROUP, UUID.randomUUID().toString(), BOB, "like"));
        assertReason(MessageService.Rejected.Reason.NOT_FOUND, () -> service.react(DIRECT, message.getMessageId(), BOB, "like"));
    }

    @Test
    void catchUpPagesThroughOneConversationInSeqOrder() {
        for (int i = 1; i <= 5; i++) {
            text(DIRECT, i % 2 == 0 ? BOB : ALICE, "m" + i);
        }
        text(GROUP, CAROL, "elsewhere");

        MessageService.MessagePage first = service.messages(DIRECT, BOB, 1L, null, 2);
        MessageService.MessagePage last = service.messages(DIRECT, BOB, 3L, null, 2);
        MessageService.MessagePage nothingNew = service.messages(DIRECT, BOB, 5L, null, 2);
        MessageService.MessagePage fromStart = service.messages(DIRECT, BOB, null, null, null);

        assertEquals(List.of(2L, 3L), first.messages().stream().map(Message::getSeq).toList());
        assertTrue(first.hasMore());
        assertEquals(List.of(4L, 5L), last.messages().stream().map(Message::getSeq).toList());
        assertFalse(last.hasMore());
        assertEquals(List.of(), nothingNew.messages());
        assertFalse(nothingNew.hasMore());
        assertEquals(5, fromStart.messages().size());
    }

    @Test
    void catchUpKeepsDeletedMessagesSoNoSeqSlotLooksMissing() {
        Message gone = text(DIRECT, ALICE, "oops").getMessage();
        text(DIRECT, BOB, "after");
        service.delete(DIRECT, gone.getMessageId(), ALICE);

        List<Message> caughtUp = service.messages(DIRECT, BOB, 0L, null, null).messages();

        assertEquals(List.of(1L, 2L), caughtUp.stream().map(Message::getSeq).toList());
        assertEquals(MessageStatus.DELETED, caughtUp.get(0).getStatus());
    }

    @Test
    void idsInUppercaseMeanTheSameAsLowercaseAndComeBackLowercase() {
        String clientMsgId = UUID.randomUUID().toString();
        Message sent = service.send(DIRECT.toUpperCase(), ALICE.toUpperCase(), clientMsgId.toUpperCase(),
            new MessageContent.Text("hi"), null, null).getMessage();
        MessageService.Sent retry = service.send(DIRECT, ALICE, clientMsgId, new MessageContent.Text("hi"), null, null);

        assertEquals(DIRECT, sent.getConversationId());
        assertEquals(ALICE, sent.getSenderId());
        assertEquals(clientMsgId, sent.getClientMsgId());
        assertFalse(retry.isCreated());
        assertEquals(sent.getMessageId(), retry.getMessage().getMessageId());
        service.react(DIRECT.toUpperCase(), sent.getMessageId().toUpperCase(), BOB.toUpperCase(), "like");
        assertEquals(BOB, service.reactions(DIRECT, sent.getMessageId(), ALICE).get(0).getUserId());
    }

    @Test
    void aUserThatIsNotAUuidIsTreatedAsMissing() {
        assertReason(MessageService.Rejected.Reason.UNAUTHENTICATED, () -> text(DIRECT, "alice", "hi"));
    }

    @Test
    void catchUpIsForMembersAndRejectsBadPaging() {
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.messages(DIRECT, CAROL, 0L, null, null));
        assertReason(MessageService.Rejected.Reason.UNAUTHENTICATED, () -> service.messages(DIRECT, null, 0L, null, null));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.messages(DIRECT, ALICE, -1L, null, null));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.messages(DIRECT, ALICE, 0L, null, 0));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.messages(DIRECT, ALICE, 0L, null, MessageService.MESSAGES_MAX_LIMIT + 1));
    }

    @Test
    void directConversationIsOnePerPairWhoeverOpensIt() {
        String dave = "aaaaaaaa-0000-4000-8000-000000000004";

        MessageService.Opened first = service.openDirect(CAROL, dave);
        MessageService.Opened again = service.openDirect(dave.toUpperCase(), CAROL);
        MessageService.Opened setUpPair = service.openDirect(BOB, ALICE);

        assertTrue(first.created());
        assertFalse(again.created());
        assertEquals(first.conversation().conversation().getConversationId(),
            again.conversation().conversation().getConversationId());
        assertEquals(ConversationType.DIRECT, first.conversation().conversation().getType());
        assertEquals(Set.of(CAROL, dave), first.conversation().conversation().getMemberIds());
        assertFalse(setUpPair.created());
        assertEquals(DIRECT, setUpPair.conversation().conversation().getConversationId());
        assertReason(MessageService.Rejected.Reason.CONFLICT,
            () -> service.openConversation(null, ConversationType.DIRECT, List.of(BOB, ALICE)));
    }

    @Test
    void directConversationNeedsAnotherValidUser() {
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.openDirect(ALICE, ALICE.toUpperCase()));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.openDirect(ALICE, "bob"));
        assertReason(MessageService.Rejected.Reason.UNAUTHENTICATED, () -> service.openDirect(null, BOB));
    }

    @Test
    void conversationListIsNewestActivityFirstWithTheLastMessageAndPages() throws Exception {
        text(DIRECT, ALICE, "older");
        Thread.sleep(5);
        Message newest = text(GROUP, BOB, "newer").getMessage();

        MessageService.ConversationPage first = service.conversations(ALICE, null, 1);
        MessageService.ConversationPage second = service.conversations(ALICE, first.nextCursor(), 1);
        MessageService.ConversationPage carol = service.conversations(CAROL, null, null);

        assertEquals(GROUP, first.conversations().get(0).conversation().getConversationId());
        assertEquals(newest.getMessageId(), first.conversations().get(0).lastMessage().getMessageId());
        assertNotNull(first.nextCursor());
        assertEquals(DIRECT, second.conversations().get(0).conversation().getConversationId());
        assertEquals("older", ((MessageContent.Text) second.conversations().get(0).lastMessage().getContent()).text());
        assertNull(second.nextCursor());
        assertEquals(List.of(GROUP), carol.conversations().stream().map(row -> row.conversation().getConversationId()).toList());
    }

    @Test
    void conversationListRejectsBadCursorAndLimit() {
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.conversations(ALICE, "yesterday", null));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.conversations(ALICE, "12_not-a-uuid", null));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.conversations(ALICE, null, MessageService.CONVERSATIONS_MAX_LIMIT + 1));
    }

    @Test
    void oneConversationIsForMembersOnly() {
        Message last = text(DIRECT, BOB, "hi").getMessage();

        assertEquals(last.getMessageId(), service.conversation(DIRECT, ALICE).lastMessage().getMessageId());
        assertNull(service.conversation(GROUP, ALICE).lastMessage());
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.conversation(DIRECT, CAROL));
    }

    @Test
    void historyStartsAtTheNewestPageAndScrollsBack() {
        for (int i = 1; i <= 5; i++) {
            text(DIRECT, ALICE, "m" + i);
        }

        MessageService.MessagePage newest = service.messages(DIRECT, BOB, null, null, 2);
        MessageService.MessagePage older = service.messages(DIRECT, BOB, null, 4L, 2);
        MessageService.MessagePage oldest = service.messages(DIRECT, BOB, null, 2L, 2);

        assertEquals(List.of(4L, 5L), newest.messages().stream().map(Message::getSeq).toList());
        assertTrue(newest.hasMore());
        assertEquals(List.of(2L, 3L), older.messages().stream().map(Message::getSeq).toList());
        assertTrue(older.hasMore());
        assertEquals(List.of(1L), oldest.messages().stream().map(Message::getSeq).toList());
        assertFalse(oldest.hasMore());
    }

    @Test
    void historyAndCatchUpCannotBeMixed() {
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.messages(DIRECT, BOB, 1L, 5L, null));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.messages(DIRECT, BOB, null, 0L, null));
    }

    private MessageService.Sent text(String conversationId, String senderId, String text) {
        return service.send(conversationId, senderId, UUID.randomUUID().toString(), new MessageContent.Text(text), null, null);
    }

    private static void assertReason(MessageService.Rejected.Reason reason, Runnable action) {
        assertEquals(reason, assertThrows(MessageService.Rejected.class, action::run).getReason());
    }
}
