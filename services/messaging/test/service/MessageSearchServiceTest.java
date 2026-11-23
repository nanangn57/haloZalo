package service;

import event.RecordingEvents;
import member.ConversationMembers;
import member.StandaloneBlockChecker;
import member.StandaloneFriendshipChecker;
import member.StandaloneGroupMembers;
import message.Message;
import message.MessageContent;
import conversation.ConversationType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;
import storage.MemoryReadMarkers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSearchServiceTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String DIRECT = "cccccccc-0000-4000-8000-000000000001";

    private MessageService service;

    @BeforeEach
    void setUp() {
        MemoryConversations conversations = new MemoryConversations();
        MemoryMessages messages = new MemoryMessages(conversations);
        service = new MessageService(conversations, messages, messages, new MemoryReactions(),
            new MemoryReadMarkers(messages), conversations.prefs(), new ConversationMembers(new StandaloneGroupMembers()),
            new StandaloneFriendshipChecker(), new StandaloneBlockChecker(), new RecordingEvents());
        service.openConversation(DIRECT, ConversationType.DIRECT, List.of(ALICE, BOB));
    }

    @Test
    void matchesIgnoreCaseAndSkipNonTextAndDeleted() {
        text("Meeting at NOON");
        text("lunch?");
        Message gone = text("noon is cancelled");
        service.send(DIRECT, ALICE, UUID.randomUUID().toString(), new MessageContent.Emotion("like"), null, null);
        service.send(DIRECT, ALICE, UUID.randomUUID().toString(),
            new MessageContent.Image("https://cdn/noon.png", null, null, null, "noon"), null, null);
        text("see you at Noon");
        service.delete(DIRECT, gone.getMessageId(), ALICE);

        List<String> found = bodies(service.messages(DIRECT, BOB, null, null, null, "noon"));

        assertEquals(List.of("Meeting at NOON", "see you at Noon"), found);
    }

    @Test
    void blankQueryMeansNoSearchAndTooLongIsRejected() {
        text("one");
        text("two");

        assertEquals(2, service.messages(DIRECT, BOB, null, null, null, "  ").messages().size());
        assertEquals(2, service.messages(DIRECT, BOB, null, null, null, null).messages().size());
        MessageService.Rejected rejected = assertThrows(MessageService.Rejected.class,
            () -> service.messages(DIRECT, BOB, null, null, null, "x".repeat(257)));
        assertEquals(MessageService.Rejected.Reason.VALIDATION, rejected.getReason());
    }

    @Test
    void paginationParamsStillApplyToTheMatches() {
        for (int i = 1; i <= 5; i++) {
            text("hit " + i);
            text("miss " + i);
        }

        MessageService.MessagePage newest = service.messages(DIRECT, BOB, null, null, 2, "hit");
        MessageService.MessagePage older = service.messages(
            DIRECT, BOB, null, newest.messages().get(0).getSeq(), 2, "hit");
        MessageService.MessagePage forward = service.messages(DIRECT, BOB, 0L, null, 2, "hit");
        MessageService.MessagePage forwardNext = service.messages(
            DIRECT, BOB, forward.messages().get(1).getSeq(), null, 10, "hit");

        assertEquals(List.of("hit 4", "hit 5"), bodies(newest));
        assertTrue(newest.hasMore());
        assertEquals(List.of("hit 2", "hit 3"), bodies(older));
        assertTrue(older.hasMore());
        assertEquals(List.of("hit 1", "hit 2"), bodies(forward));
        assertTrue(forward.hasMore());
        assertEquals(List.of("hit 3", "hit 4", "hit 5"), bodies(forwardNext));
        assertFalse(forwardNext.hasMore());
    }

    @Test
    void searchStillNeedsMembershipAndAValidSeqWindow() {
        text("secret");

        assertEquals(MessageService.Rejected.Reason.FORBIDDEN, assertThrows(MessageService.Rejected.class,
            () -> service.messages(DIRECT, CAROL, null, null, null, "secret")).getReason());
        assertEquals(MessageService.Rejected.Reason.VALIDATION, assertThrows(MessageService.Rejected.class,
            () -> service.messages(DIRECT, BOB, 1L, 5L, null, "secret")).getReason());
    }

    @Test
    void percentAndUnderscoreAreNotWildcards() {
        text("100% sure");
        text("a_b");
        text("axb");

        assertEquals(List.of("100% sure"), bodies(service.messages(DIRECT, BOB, null, null, null, "100%")));
        assertEquals(List.of("a_b"), bodies(service.messages(DIRECT, BOB, null, null, null, "a_b")));
    }

    private Message text(String body) {
        return service.send(DIRECT, ALICE, UUID.randomUUID().toString(), new MessageContent.Text(body), null, null).getMessage();
    }

    private static List<String> bodies(MessageService.MessagePage page) {
        return page.messages().stream().map(row -> ((MessageContent.Text) row.getContent()).text()).toList();
    }
}
