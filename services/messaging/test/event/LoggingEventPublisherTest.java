package event;

import message.Message;
import message.MessageContent;
import message.MessageStatus;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import java.time.Instant;

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
    void envelopeCarriesTheContractMessageAsPayload() {
        String envelope = LoggingEventPublisher.envelope(MESSAGE);

        assertEquals("messaging.message.created", JsonPath.read(envelope, "$.type"));
        assertEquals("messaging", JsonPath.read(envelope, "$.producer"));
        assertEquals(1, (int) JsonPath.read(envelope, "$.payload.seq"));
        assertEquals("TEXT", JsonPath.read(envelope, "$.payload.type"));
        assertEquals("say \"hi\"", JsonPath.read(envelope, "$.payload.body"));
        assertEquals("2026-09-28T09:00:00Z", JsonPath.read(envelope, "$.payload.createdAt"));
        assertFalse(envelope.contains("\"status\""));
    }

    @Test
    void eachEnvelopeHasItsOwnEventId() {
        String first = JsonPath.read(LoggingEventPublisher.envelope(MESSAGE), "$.eventId");
        String second = JsonPath.read(LoggingEventPublisher.envelope(MESSAGE), "$.eventId");

        assertNotEquals(first, second);
    }
}
