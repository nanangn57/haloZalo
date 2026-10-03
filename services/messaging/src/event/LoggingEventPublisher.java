package event;

import api.MessageResponse;
import message.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public final class LoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void publishMessageCreated(Message message) {
        log.info(envelope(message));
    }

    static String envelope(Message message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", "messaging.message.created");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", "messaging");
        envelope.put("payload", new MessageResponse(message));
        try {
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write event", ex);
        }
    }
}
