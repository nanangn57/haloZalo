package event;

/**
 * Payload of messaging.reaction.added and messaging.reaction.removed.
 */
public record ReactionPayload(String messageId, String conversationId, String userId, String code) {
}
