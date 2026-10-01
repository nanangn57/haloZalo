package event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingEventPublisherTest {
    @Test
    void envelopeCarriesLoginContract() {
        String envelope = LoggingEventPublisher.envelope("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        assertTrue(envelope.contains("\"type\":\"identity.user.logged_in\""));
        assertTrue(envelope.contains("\"producer\":\"identity\""));
        assertTrue(envelope.contains("\"payload\":{\"userId\":\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\"}"));
        assertFalse(envelope.contains("password"));
        assertFalse(envelope.contains("email"));
    }

    @Test
    void eachEnvelopeHasItsOwnEventId() {
        String first = LoggingEventPublisher.envelope("user-1");
        String second = LoggingEventPublisher.envelope("user-1");

        assertNotEquals(eventId(first), eventId(second));
    }

    private static String eventId(String envelope) {
        int start = envelope.indexOf("\"eventId\":\"") + "\"eventId\":\"".length();
        return envelope.substring(start, envelope.indexOf('"', start));
    }
}
