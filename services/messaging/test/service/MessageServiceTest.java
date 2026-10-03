package service;

import conversation.ConversationType;
import message.Message;
import message.MessageContent;
import message.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String DIRECT = "cccccccc-0000-4000-8000-000000000001";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000002";

    private MemoryMessages messages;
    private List<Message> published;
    private MessageService service;

    @BeforeEach
    void setUp() {
        messages = new MemoryMessages();
        published = new ArrayList<>();
        service = new MessageService(new MemoryConversations(), messages, new MemoryReactions(), published::add);
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
        assertEquals(List.of(first, second, inGroup), published);
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
        assertEquals(2, published.size());
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

        assertEquals(MessageStatus.DELETED, deleted.getStatus());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(deleted.getDeletedAt(), deleted.getUpdatedAt());
        assertEquals(MessageStatus.DELETED, messages.findById(original.getMessageId()).getStatus());
        assertEquals(original.getMessageId(), messages.findById(reply.getMessageId()).getReplyTo());
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
        assertEquals(2, service.reactions(GROUP, message.getMessageId(), ALICE).size());

        Message direct = text(DIRECT, ALICE, "private").getMessage();
        assertReason(MessageService.Rejected.Reason.FORBIDDEN, () -> service.react(DIRECT, direct.getMessageId(), CAROL, "like"));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.react(GROUP, message.getMessageId(), BOB, "heart"));
        assertReason(MessageService.Rejected.Reason.NOT_FOUND, () -> service.react(GROUP, UUID.randomUUID().toString(), BOB, "like"));
        assertReason(MessageService.Rejected.Reason.NOT_FOUND, () -> service.react(DIRECT, message.getMessageId(), BOB, "like"));
    }

    private MessageService.Sent text(String conversationId, String senderId, String text) {
        return service.send(conversationId, senderId, UUID.randomUUID().toString(), new MessageContent.Text(text), null, null);
    }

    private static void assertReason(MessageService.Rejected.Reason reason, Runnable action) {
        assertEquals(reason, assertThrows(MessageService.Rejected.class, action::run).getReason());
    }
}
