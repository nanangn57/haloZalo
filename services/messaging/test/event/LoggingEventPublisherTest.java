package event;

import api.MessageResponse;
import message.Message;
import message.MessageContent;
import message.MessageStatus;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LoggingEventPublisherTest {
    private static final Message MESSAGE = new Message(
        "8a1b2c3d-4e5f-6789-abcd-ef0123456789", "11111111-2222-4333-8444-555555555555", 1,
        "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", "99999999-8888-4777-8666-555555555555",
        new MessageContent.Text("say \"hi\""), null, null, null, MessageStatus.SENT,
        Instant.parse("2026-09-28T09:00:00Z"), Instant.parse("2026-09-28T09:00:00Z"), null);

    @Test
    void createdEnvelopeCarriesTheContractMessageAsPayload() {
        String envelope = LoggingEventPublisher.envelope(LoggingEventPublisher.MESSAGE_CREATED, new MessageResponse(MESSAGE));

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

        String envelope = LoggingEventPublisher.envelope(
            LoggingEventPublisher.MESSAGE_DELETED, MessageDeletedPayload.of(deleted));

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

        String added = LoggingEventPublisher.envelope(LoggingEventPublisher.REACTION_ADDED, payload);
        String removed = LoggingEventPublisher.envelope(LoggingEventPublisher.REACTION_REMOVED, payload);

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
    void eachEnvelopeHasItsOwnEventId() {
        MessageResponse payload = new MessageResponse(MESSAGE);
        String first = JsonPath.read(LoggingEventPublisher.envelope(LoggingEventPublisher.MESSAGE_CREATED, payload), "$.eventId");
        String second = JsonPath.read(LoggingEventPublisher.envelope(LoggingEventPublisher.MESSAGE_CREATED, payload), "$.eventId");

        assertNotEquals(first, second);
    }
}
