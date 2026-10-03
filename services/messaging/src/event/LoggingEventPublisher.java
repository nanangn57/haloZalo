package event;

import api.MessageResponse;
import message.Message;
import reaction.MessageReaction;

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
    static final String MESSAGE_CREATED = "messaging.message.created";
    static final String MESSAGE_DELETED = "messaging.message.deleted";
    static final String REACTION_ADDED = "messaging.reaction.added";
    static final String REACTION_REMOVED = "messaging.reaction.removed";

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void publishMessageCreated(Message message) {
        log.info(envelope(MESSAGE_CREATED, new MessageResponse(message)));
    }

    @Override
    public void publishMessageDeleted(Message message) {
        log.info(envelope(MESSAGE_DELETED, MessageDeletedPayload.of(message)));
    }

    @Override
    public void publishReactionAdded(String conversationId, MessageReaction reaction) {
        log.info(envelope(REACTION_ADDED, new ReactionPayload(
            reaction.getMessageId(), conversationId, reaction.getUserId(), reaction.getCode())));
    }

    @Override
    public void publishReactionRemoved(String conversationId, String messageId, String userId, String code) {
        log.info(envelope(REACTION_REMOVED, new ReactionPayload(messageId, conversationId, userId, code)));
    }

    static String envelope(String type, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", "messaging");
        envelope.put("payload", payload);
        try {
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write event", ex);
        }
    }
}
