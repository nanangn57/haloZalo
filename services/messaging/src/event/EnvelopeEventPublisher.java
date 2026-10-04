package event;

import api.MessageResponse;
import conversation.Conversation;
import message.Message;
import reaction.MessageReaction;
import realtime.RealtimeBus;
import realtime.RealtimeFrame;
import storage.ConversationRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps each change in the shared envelope, logs it for the event bus, and fans it out to every socket of
 * every conversation member. The sender is a member too, so the sender's other devices get it as well.
 */
@Component
public final class EnvelopeEventPublisher implements EventPublisher {
    static final String MESSAGE_CREATED = "messaging.message.created";
    static final String MESSAGE_DELETED = "messaging.message.deleted";
    static final String REACTION_ADDED = "messaging.reaction.added";
    static final String REACTION_REMOVED = "messaging.reaction.removed";

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventPublisher.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConversationRepository conversations;
    private final RealtimeBus realtime;

    public EnvelopeEventPublisher(ConversationRepository conversations, RealtimeBus realtime) {
        this.conversations = conversations;
        this.realtime = realtime;
    }

    @Override
    public void publishMessageCreated(Message message) {
        emit(message.getConversationId(), MESSAGE_CREATED, new MessageResponse(message));
    }

    @Override
    public void publishMessageDeleted(Message message) {
        emit(message.getConversationId(), MESSAGE_DELETED, MessageDeletedPayload.of(message));
    }

    @Override
    public void publishReactionAdded(String conversationId, MessageReaction reaction) {
        emit(conversationId, REACTION_ADDED, new ReactionPayload(
            reaction.getMessageId(), conversationId, reaction.getUserId(), reaction.getCode()));
    }

    @Override
    public void publishReactionRemoved(String conversationId, String messageId, String userId, String code) {
        emit(conversationId, REACTION_REMOVED, new ReactionPayload(messageId, conversationId, userId, code));
    }

    private void emit(String conversationId, String type, Object payload) {
        String envelope = envelope(type, payload);
        log.info(envelope);
        // The change is already stored. A failed push must not fail the request: clients catch up by seq.
        try {
            Conversation conversation = conversations.findById(conversationId);
            if (conversation != null) {
                realtime.publish(new RealtimeFrame(List.copyOf(conversation.getMemberIds()), envelope));
            }
        } catch (RuntimeException ex) {
            log.warn("Could not push {} for conversation {}", type, conversationId, ex);
        }
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
