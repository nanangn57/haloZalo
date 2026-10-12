package event;

import api.MessageResponse;
import conversation.Conversation;
import member.ConversationMembers;
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
import java.util.function.Supplier;

/**
 * Wraps each change in the shared envelope, logs it for the event bus, and fans it out to every socket of
 * every conversation member. The sender is a member too, so the sender's other devices get it as well.
 * Group members are read from identity at the moment of sending, so someone removed from a group gets nothing.
 */
@Component
public final class EnvelopeEventPublisher implements EventPublisher {
    static final String MESSAGE_CREATED = "messaging.message.created";
    static final String MESSAGE_DELETED = "messaging.message.deleted";
    static final String REACTION_ADDED = "messaging.reaction.added";
    static final String REACTION_REMOVED = "messaging.reaction.removed";
    static final String READ_UPDATED = "messaging.read.updated";

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventPublisher.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConversationRepository conversations;
    private final ConversationMembers members;
    private final RealtimeBus realtime;

    public EnvelopeEventPublisher(ConversationRepository conversations, ConversationMembers members, RealtimeBus realtime) {
        this.conversations = conversations;
        this.members = members;
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

    @Override
    public void publishReadUpdated(String conversationId, String userId, long lastReadSeq) {
        String envelope = envelope(READ_UPDATED, new ReadUpdatedPayload(conversationId, userId, lastReadSeq));
        log.info(envelope);
        push(READ_UPDATED, conversationId, () -> List.of(userId), envelope);
    }

    private void emit(String conversationId, String type, Object payload) {
        String envelope = envelope(type, payload);
        log.info(envelope);
        push(type, conversationId, () -> {
            Conversation conversation = conversations.findById(conversationId);
            return conversation == null ? List.of() : List.copyOf(members.memberIds(conversation));
        }, envelope);
    }

    /**
     * The change is already stored. A failed push must not fail the request: clients catch up by seq.
     */
    private void push(String type, String conversationId, Supplier<List<String>> recipients, String envelope) {
        try {
            List<String> to = recipients.get();
            if (!to.isEmpty()) {
                realtime.publish(new RealtimeFrame(to, envelope));
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
