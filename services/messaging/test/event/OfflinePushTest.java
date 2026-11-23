package event;

import conversation.Conversation;
import conversation.ConversationPrefsPatch;
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

/**
 * Offline push goes to members without a socket, except those who muted the conversation. The socket frame is
 * not affected by mute.
 */
class OfflinePushTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000003";

    private final List<String> pushedTo = new ArrayList<>();
    private final List<String> previews = new ArrayList<>();
    private final List<RealtimeFrame> frames = new ArrayList<>();
    private MemoryPrefs prefs;
    private EnvelopeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        StandaloneGroupMembers groups = new StandaloneGroupMembers();
        groups.replace(GROUP, Set.of(ALICE, BOB, CAROL));
        MemoryConversations conversations = new MemoryConversations();
        conversations.insert(new Conversation(GROUP, ConversationType.GROUP, Set.of(), 1, Instant.now()));
        prefs = new MemoryPrefs();
        RealtimeSessions nobodyOnline = new RealtimeSessions(new RealtimeProperties(
            List.of("*"), Duration.ofSeconds(25), Duration.ofSeconds(60), Duration.ofSeconds(10),
            DataSize.ofKilobytes(512), DataSize.ofKilobytes(8)));
        publisher = new EnvelopeEventPublisher(conversations, new ConversationMembers(groups), frames::add,
            envelope -> { }, nobodyOnline, (ids, conversationId, preview) -> {
                pushedTo.addAll(ids);
                previews.add(preview);
            }, prefs);
    }

    @Test
    void everyOfflineMemberExceptTheSenderGetsAPushWhenNobodyMuted() {
        publisher.publishMessageCreated(message(new MessageContent.Text("hello")));

        assertEquals(Set.of(BOB, CAROL), Set.copyOf(pushedTo));
        assertEquals(List.of("hello"), previews);
    }

    @Test
    void aMutedMemberGetsNoPushButTheSocketFrameStillGoesToEveryone() {
        prefs.update(BOB, GROUP, new ConversationPrefsPatch(null, true, null), Instant.now());

        publisher.publishMessageCreated(message(new MessageContent.Text("hello")));

        assertEquals(List.of(CAROL), pushedTo);
        assertEquals(1, frames.size());
        assertEquals(Set.of(ALICE, BOB, CAROL), Set.copyOf(frames.get(0).recipients()));
    }

    @Test
    void mutingOneConversationDoesNotMuteTheOthersAndUnmuteRestoresPush() {
        prefs.update(BOB, "cccccccc-0000-4000-8000-0000000000ff", new ConversationPrefsPatch(null, true, null), Instant.now());
        publisher.publishMessageCreated(message(new MessageContent.Text("one")));
        assertEquals(Set.of(BOB, CAROL), Set.copyOf(pushedTo));

        pushedTo.clear();
        prefs.update(BOB, GROUP, new ConversationPrefsPatch(null, true, null), Instant.now());
        prefs.update(CAROL, GROUP, new ConversationPrefsPatch(null, true, null), Instant.now());
        publisher.publishMessageCreated(message(new MessageContent.Text("two")));
        assertEquals(List.of(), pushedTo);

        prefs.update(BOB, GROUP, new ConversationPrefsPatch(null, false, null), Instant.now());
        publisher.publishMessageCreated(message(new MessageContent.Text("three")));
        assertEquals(List.of(BOB), pushedTo);
    }

    @Test
    void audioHasItsOwnPreview() {
        publisher.publishMessageCreated(message(new MessageContent.Audio("https://cdn/v.m4a", "audio/mp4", 10, 3, null)));

        assertEquals(List.of("Sent a voice message"), previews);
    }

    private static Message message(MessageContent content) {
        return new Message("8a1b2c3d-4e5f-6789-abcd-ef0123456789", GROUP, 1, ALICE,
            "99999999-8888-4777-8666-555555555555", content, null, null, null, MessageStatus.SENT,
            Instant.parse("2026-09-28T09:00:00Z"), Instant.parse("2026-09-28T09:00:00Z"), null);
    }
}
