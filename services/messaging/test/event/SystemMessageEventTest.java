package event;

import conversation.Conversation;
import conversation.ConversationType;
import member.ConversationMembers;
import member.StandaloneGroupMembers;
import message.Message;
import message.MessageContent;
import message.MessageStatus;
import realtime.RealtimeFrame;
import realtime.RealtimeProperties;
import realtime.RealtimeSessions;
import storage.MemoryConversations;
import storage.MemoryPrefs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SYSTEM message reaches every member's sockets, is not counted by analytics, and never sends a push.
 */
class SystemMessageEventTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000003";

    private final List<String> pushedTo = new ArrayList<>();
    private final List<String> recorded = new ArrayList<>();
    private final List<RealtimeFrame> frames = new ArrayList<>();
    private EnvelopeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        StandaloneGroupMembers groups = new StandaloneGroupMembers();
        groups.replace(GROUP, Set.of(ALICE, BOB));
        MemoryConversations conversations = new MemoryConversations();
        conversations.insert(new Conversation(GROUP, ConversationType.GROUP, Set.of(), 1, Instant.now()));
        RealtimeSessions nobodyOnline = new RealtimeSessions(new RealtimeProperties(
            List.of("*"), Duration.ofSeconds(25), Duration.ofSeconds(60), Duration.ofSeconds(10),
            DataSize.ofKilobytes(512), DataSize.ofKilobytes(8)));
        publisher = new EnvelopeEventPublisher(conversations, new ConversationMembers(groups), frames::add,
            recorded::add, nobodyOnline, (ids, conversationId, preview) -> pushedTo.addAll(ids), new MemoryPrefs());
    }

    @Test
    void membersGetTheFrameWithoutASenderIdButNobodyIsPushedOrCounted() {
        publisher.publishMessageCreated(system("Alice added Bob"));

        assertEquals(1, frames.size());
        assertEquals(Set.of(ALICE, BOB), Set.copyOf(frames.get(0).recipients()));
        assertTrue(frames.get(0).envelope().contains("\"type\":\"SYSTEM\""));
        assertTrue(frames.get(0).envelope().contains("\"body\":\"Alice added Bob\""));
        assertFalse(frames.get(0).envelope().contains("senderId"));
        assertEquals(List.of(), pushedTo);
        assertEquals(List.of(), recorded);
    }

    @Test
    void aTextMessageIsStillCountedAndPushed() {
        publisher.publishMessageCreated(new Message("8a1b2c3d-4e5f-6789-abcd-ef0123456789", GROUP, 2, ALICE,
            "99999999-8888-4777-8666-555555555555", new MessageContent.Text("hi"), null, null, null,
            MessageStatus.SENT, Instant.now(), Instant.now(), null));

        assertEquals(1, recorded.size());
        assertEquals(List.of(BOB), pushedTo);
    }

    private static Message system(String text) {
        return new Message("8a1b2c3d-4e5f-6789-abcd-ef0123456789", GROUP, 1, null,
            "99999999-8888-4777-8666-555555555555", new MessageContent.System(text), null, null, null,
            MessageStatus.SENT, Instant.parse("2026-09-28T09:00:00Z"), Instant.parse("2026-09-28T09:00:00Z"), null);
    }
}
