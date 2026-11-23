package service;

import conversation.ConversationType;
import event.RecordingEvents;
import member.ConversationMembers;
import member.StandaloneBlockChecker;
import member.StandaloneFriendshipChecker;
import member.StandaloneGroupMembers;
import message.Message;
import message.MessageContent;
import message.MessageStatus;
import message.MessageType;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;
import storage.MemoryReadMarkers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMessageServiceTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000002";

    private MemoryConversations conversations;
    private StandaloneGroupMembers groups;
    private MemoryMessages messages;
    private RecordingEvents events;
    private MemoryReadMarkers readMarkers;
    private MessageService service;

    @BeforeEach
    void setUp() {
        events = new RecordingEvents();
        conversations = new MemoryConversations();
        messages = new MemoryMessages(conversations);
        readMarkers = new MemoryReadMarkers(messages);
        groups = new StandaloneGroupMembers();
        service = new MessageService(conversations, messages, messages, new MemoryReactions(), readMarkers,
            conversations.prefs(), new ConversationMembers(groups), new StandaloneFriendshipChecker(),
            new StandaloneBlockChecker(), events);
        groups.replace(GROUP, Set.of(ALICE, BOB));
        service.openConversation(GROUP, ConversationType.GROUP, null);
    }

    @Test
    void postSystemStoresASenderlessSystemMessageWithTheNextSeqAndPublishesIt() {
        service.send(GROUP, ALICE, UUID.randomUUID().toString(), new MessageContent.Text("hi"), null, null);

        Message posted = service.postSystem(GROUP, "Alice added Bob");

        assertEquals(MessageType.SYSTEM, posted.getType());
        assertNull(posted.getSenderId());
        assertEquals(new MessageContent.System("Alice added Bob"), posted.getContent());
        assertEquals(2, posted.getSeq());
        assertEquals(MessageStatus.SENT, posted.getStatus());
        assertNotNull(posted.getClientMsgId());
        assertEquals(posted, messages.findById(posted.getMessageId()));
        assertEquals(posted, events.created.get(1));
    }

    @Test
    void eachSystemMessageGetsItsOwnSeqAndNeverCollidesWithARetry() {
        Message first = service.postSystem(GROUP, "Alice added Bob");
        Message second = service.postSystem(GROUP, "Alice added Bob");

        assertEquals(1, first.getSeq());
        assertEquals(2, second.getSeq());
        assertEquals(2, events.created.size());
    }

    @Test
    void aSystemMessageMovesNoReadMarkerAndIsNeverUnread() {
        service.postSystem(GROUP, "Alice added Bob");

        assertEquals(0, readMarkers.readStates(ALICE, List.of(GROUP)).get(GROUP).lastReadSeq());
        assertEquals(0, readMarkers.readStates(BOB, List.of(GROUP)).get(GROUP).unreadCount());
        assertTrue(events.reads.isEmpty());
    }

    @Test
    void membersReadItInHistoryAsTheLatestMessage() {
        Message posted = service.postSystem(GROUP, "Alice added Bob");

        List<Message> page = service.messages(GROUP, BOB, null, null, null).messages();

        assertEquals(List.of(posted), page);
        assertEquals(posted, service.conversation(GROUP, BOB).lastMessage());
    }

    @Test
    void aGroupConversationNotOpenedYetIsOpenedForTheFirstSystemMessage() {
        String other = UUID.randomUUID().toString();
        groups.replace(other, Set.of(ALICE));

        Message posted = service.postSystem(other, "Alice left the group");

        assertEquals(other, posted.getConversationId());
        assertEquals(ConversationType.GROUP, conversations.findById(other).getType());
        assertEquals(1, posted.getSeq());
    }

    @Test
    void blankOrOversizedTextAndBadIdsAreRejected() {
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.postSystem(GROUP, " "));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.postSystem(GROUP, null));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.postSystem(GROUP, "x".repeat(4097)));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.postSystem("nope", "text"));
        assertTrue(events.created.isEmpty());
    }

    @Test
    void membersCannotDeleteForwardOrReactToASystemMessage() {
        Message posted = service.postSystem(GROUP, "Alice added Bob");

        assertReason(MessageService.Rejected.Reason.FORBIDDEN,
            () -> service.delete(GROUP, posted.getMessageId(), ALICE));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.forward(GROUP, ALICE, UUID.randomUUID().toString(), posted.getMessageId()));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.react(GROUP, posted.getMessageId(), ALICE, "like"));
        assertInstanceOf(MessageContent.System.class, messages.findById(posted.getMessageId()).getContent());
        assertTrue(events.deleted.isEmpty());
    }

    private static void assertReason(MessageService.Rejected.Reason reason, org.junit.jupiter.api.function.Executable action) {
        MessageService.Rejected rejected = assertThrows(MessageService.Rejected.class, action);
        assertEquals(reason, rejected.getReason());
    }
}
