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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pin, mute and archive are per user: they change what the caller lists, never what the other member sees.
 */
class ConversationPrefsServiceTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String WITH_BOB = "cccccccc-0000-4000-8000-000000000001";
    private static final String WITH_CAROL = "cccccccc-0000-4000-8000-000000000002";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000003";

    private MessageService service;

    @BeforeEach
    void setUp() {
        MemoryConversations conversations = new MemoryConversations();
        MemoryMessages messages = new MemoryMessages(conversations);
        StandaloneGroupMembers groups = new StandaloneGroupMembers();
        service = new MessageService(conversations, messages, messages, new MemoryReactions(),
            new MemoryReadMarkers(messages), conversations.prefs(), new ConversationMembers(groups),
            new StandaloneFriendshipChecker(), new StandaloneBlockChecker(), new RecordingEvents());
        service.openConversation(WITH_BOB, ConversationType.DIRECT, List.of(ALICE, BOB));
        service.openConversation(WITH_CAROL, ConversationType.DIRECT, List.of(ALICE, CAROL));
        groups.replace(GROUP, Set.of(ALICE, BOB, CAROL));
        service.openConversation(GROUP, ConversationType.GROUP, null);
    }

    @Test
    void patchChangesOnlyTheGivenFieldsAndKeepsTheRest() {
        MessageService.PrefsChange first = service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(true, null, null));
        MessageService.PrefsChange second = service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(null, true, null));
        MessageService.PrefsChange third = service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(false, null, true));

        assertEquals(WITH_BOB, first.conversationId());
        assertTrue(first.prefs().pinned());
        assertFalse(first.prefs().muted());
        assertTrue(second.prefs().pinned());
        assertTrue(second.prefs().muted());
        assertFalse(third.prefs().pinned());
        assertTrue(third.prefs().muted());
        assertTrue(third.prefs().archived());
    }

    @Test
    void prefsBelongToTheCallerOnly() {
        service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(true, true, true));

        ConversationSummary mine = service.conversation(WITH_BOB, ALICE);
        ConversationSummary his = service.conversation(WITH_BOB, BOB);

        assertTrue(mine.prefs().pinned());
        assertTrue(mine.prefs().muted());
        assertTrue(mine.prefs().archived());
        assertFalse(his.prefs().pinned());
        assertFalse(his.prefs().muted());
        assertFalse(his.prefs().archived());
    }

    @Test
    void emptyPatchIsRejectedAndOutsidersAreForbidden() {
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(null, null, null)));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.updatePrefs(WITH_BOB, ALICE, null));
        assertReason(MessageService.Rejected.Reason.FORBIDDEN,
            () -> service.updatePrefs(WITH_BOB, CAROL, new ConversationPrefsPatch(true, null, null)));
        assertReason(MessageService.Rejected.Reason.UNAUTHENTICATED,
            () -> service.updatePrefs(WITH_BOB, "alice", new ConversationPrefsPatch(true, null, null)));
        assertReason(MessageService.Rejected.Reason.VALIDATION,
            () -> service.updatePrefs("not-a-uuid", ALICE, new ConversationPrefsPatch(true, null, null)));
    }

    @Test
    void pinnedConversationsListFirstEvenWhenOlder() {
        send(WITH_BOB, ALICE, "old");
        send(WITH_CAROL, ALICE, "newer");
        send(GROUP, ALICE, "newest");
        service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(true, null, null));

        List<String> ids = ids(service.conversations(ALICE, null, null));

        assertEquals(WITH_BOB, ids.get(0));
        assertEquals(3, ids.size());
        // Bob is in two of the three and never pinned anything.
        assertEquals(2, ids(service.conversations(BOB, null, null)).size());
    }

    @Test
    void archivedAreHiddenByDefaultAndListedOnRequest() {
        service.updatePrefs(WITH_CAROL, ALICE, new ConversationPrefsPatch(null, null, true));

        List<String> normal = ids(service.conversations(ALICE, null, null));
        List<String> archived = ids(service.conversations(ALICE, null, null, new ConversationFilter(false, true)));

        assertEquals(Set.of(WITH_BOB, GROUP), Set.copyOf(normal));
        assertEquals(List.of(WITH_CAROL), archived);

        service.updatePrefs(WITH_CAROL, ALICE, new ConversationPrefsPatch(null, null, false));
        assertEquals(3, ids(service.conversations(ALICE, null, null)).size());
        assertEquals(List.of(), ids(service.conversations(ALICE, null, null, new ConversationFilter(false, true))));
    }

    @Test
    void pinnedFirstOrderSurvivesPaging() {
        service.updatePrefs(GROUP, ALICE, new ConversationPrefsPatch(true, null, null));
        service.updatePrefs(WITH_CAROL, ALICE, new ConversationPrefsPatch(true, null, null));

        MessageService.ConversationPage first = service.conversations(ALICE, null, 1);
        MessageService.ConversationPage second = service.conversations(ALICE, first.nextCursor(), 1);
        MessageService.ConversationPage third = service.conversations(ALICE, second.nextCursor(), 1);

        assertNotNull(first.nextCursor());
        assertTrue(first.nextCursor().startsWith("p"));
        assertNotNull(second.nextCursor());
        assertNull(third.nextCursor());
        List<String> all = List.of(
            first.conversations().get(0).conversation().getConversationId(),
            second.conversations().get(0).conversation().getConversationId(),
            third.conversations().get(0).conversation().getConversationId());
        assertEquals(Set.of(GROUP, WITH_CAROL, WITH_BOB), Set.copyOf(all));
        assertEquals(WITH_BOB, all.get(2));
    }

    @Test
    void listIncludesTheCallersOwnPrefs() {
        service.updatePrefs(WITH_BOB, ALICE, new ConversationPrefsPatch(null, true, null));

        ConversationSummary bob = service.conversations(ALICE, null, null).conversations().stream()
            .filter(row -> row.conversation().getConversationId().equals(WITH_BOB)).findFirst().orElseThrow();

        assertTrue(bob.prefs().muted());
        assertFalse(bob.pendingRequest());
    }

    @Test
    void garbageCursorIsStillRejected() {
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.conversations(ALICE, "p_x", null));
        assertReason(MessageService.Rejected.Reason.VALIDATION, () -> service.conversations(ALICE, "nonsense", null));
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
