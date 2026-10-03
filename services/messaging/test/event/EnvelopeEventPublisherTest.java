package event;

import api.MessageResponse;
import conversation.Conversation;
import conversation.ConversationType;
import message.Message;
import message.MessageContent;
import message.MessageStatus;
import org.junit.jupiter.api.Test;
import reaction.MessageReaction;
import realtime.RealtimeFrame;
import storage.MemoryConversations;

import com.jayway.jsonpath.JsonPath;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EnvelopeEventPublisherTest {
    private static final Message MESSAGE = new Message(
        "8a1b2c3d-4e5f-6789-abcd-ef0123456789", "11111111-2222-4333-8444-555555555555", 1,
        "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", "99999999-8888-4777-8666-555555555555",
        new MessageContent.Text("say \"hi\""), null, null, null, MessageStatus.SENT,
        Instant.parse("2026-09-28T09:00:00Z"), Instant.parse("2026-09-28T09:00:00Z"), null);

    @Test
    void createdEnvelopeCarriesTheContractMessageAsPayload() {
        String envelope = EnvelopeEventPublisher.envelope(EnvelopeEventPublisher.MESSAGE_CREATED, new MessageResponse(MESSAGE));

        assertEquals("messaging.message.created", JsonPath.read(envelope, "$.type"));
        assertEquals("messaging", JsonPath.read(envelope, "$.producer"));
        assertEquals(1, (int) JsonPath.read(envelope, "$.payload.seq"));
        assertEquals("TEXT", JsonPath.read(envelope, "$.payload.type"));
        assertEquals("say \"hi\"", JsonPath.read(envelope, "$.payload.body"));
        assertEquals("2026-09-28T09:00:00Z", JsonPath.read(envelope, "$.payload.createdAt"));
        assertFalse(envelope.contains("\"status\""));
    }

    @Test
    void deletedEnvelopeCarriesIdsAndTimeButNoContent() {
        Message deleted = MESSAGE.softDeleted(Instant.parse("2026-09-28T09:05:00Z"));

        String envelope = EnvelopeEventPublisher.envelope(
            EnvelopeEventPublisher.MESSAGE_DELETED, MessageDeletedPayload.of(deleted));

        assertEquals("messaging.message.deleted", JsonPath.read(envelope, "$.type"));
        assertEquals(Map.of(
            "messageId", MESSAGE.getMessageId(),
            "conversationId", MESSAGE.getConversationId(),
            "deletedAt", "2026-09-28T09:05:00Z"
        ), JsonPath.read(envelope, "$.payload"));
        assertFalse(envelope.contains("say"));
    }

    @Test
    void reactionEnvelopesCarryWhoReactedWithWhatOnWhichMessage() {
        ReactionPayload payload = new ReactionPayload(
            MESSAGE.getMessageId(), MESSAGE.getConversationId(), "aaaaaaaa-0000-4000-8000-000000000002", "love");

        String added = EnvelopeEventPublisher.envelope(EnvelopeEventPublisher.REACTION_ADDED, payload);
        String removed = EnvelopeEventPublisher.envelope(EnvelopeEventPublisher.REACTION_REMOVED, payload);

        assertEquals("messaging.reaction.added", JsonPath.read(added, "$.type"));
        assertEquals("messaging.reaction.removed", JsonPath.read(removed, "$.type"));
        assertEquals(Map.of(
            "messageId", MESSAGE.getMessageId(),
            "conversationId", MESSAGE.getConversationId(),
            "userId", "aaaaaaaa-0000-4000-8000-000000000002",
            "code", "love"
        ), JsonPath.read(added, "$.payload"));
    }

    @Test
    void everyEventIsPushedToEveryMemberIncludingTheSender() {
        MemoryConversations conversations = conversationWithThreeMembers();
        List<RealtimeFrame> pushed = new ArrayList<>();
        EnvelopeEventPublisher publisher = new EnvelopeEventPublisher(conversations, pushed::add);

        publisher.publishMessageCreated(MESSAGE);
        publisher.publishMessageDeleted(MESSAGE.softDeleted(Instant.parse("2026-09-28T09:05:00Z")));
        publisher.publishReactionAdded(MESSAGE.getConversationId(),
            new MessageReaction("r-1", MESSAGE.getMessageId(), "bob", "love", Instant.now()));
        publisher.publishReactionRemoved(MESSAGE.getConversationId(), MESSAGE.getMessageId(), "bob", "love");

        assertEquals(List.of(MESSAGE_CREATED_TYPE, "messaging.message.deleted", "messaging.reaction.added", "messaging.reaction.removed"),
            pushed.stream().map(frame -> (String) JsonPath.read(frame.envelope(), "$.type")).toList());
        for (RealtimeFrame frame : pushed) {
            assertEquals(Set.of(MESSAGE.getSenderId(), "bob", "carol"), Set.copyOf(frame.recipients()));
        }
    }

    @Test
    void aFailedPushDoesNotFailTheChangeThatIsAlreadyStored() {
        EnvelopeEventPublisher publisher = new EnvelopeEventPublisher(conversationWithThreeMembers(), frame -> {
            throw new IllegalStateException("Redis is down");
        });

        assertDoesNotThrow(() -> publisher.publishMessageCreated(MESSAGE));
    }

    @Test
    void anUnknownConversationIsNotPushed() {
        List<RealtimeFrame> pushed = new ArrayList<>();

        new EnvelopeEventPublisher(new MemoryConversations(), pushed::add).publishMessageCreated(MESSAGE);

        assertEquals(List.of(), pushed);
    }

    private static MemoryConversations conversationWithThreeMembers() {
        MemoryConversations conversations = new MemoryConversations();
        conversations.insert(new Conversation(MESSAGE.getConversationId(), ConversationType.GROUP,
            Set.of(MESSAGE.getSenderId(), "bob", "carol"), 1, Instant.now()));
        return conversations;
    }

    private static final String MESSAGE_CREATED_TYPE = "messaging.message.created";

    @Test
    void eachEnvelopeHasItsOwnEventId() {
        MessageResponse payload = new MessageResponse(MESSAGE);
        String first = JsonPath.read(EnvelopeEventPublisher.envelope(EnvelopeEventPublisher.MESSAGE_CREATED, payload), "$.eventId");
        String second = JsonPath.read(EnvelopeEventPublisher.envelope(EnvelopeEventPublisher.MESSAGE_CREATED, payload), "$.eventId");

        assertNotEquals(first, second);
    }
}
