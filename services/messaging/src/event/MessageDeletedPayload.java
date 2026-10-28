package event;

import message.Message;

/**
 * Payload of messaging.message.deleted. Carries no content, so a deleted text does not travel again.
 */
public record MessageDeletedPayload(String messageId, String conversationId, String deletedAt) {
    public static MessageDeletedPayload of(Message message) {
        return new MessageDeletedPayload(
            message.getMessageId(), message.getConversationId(), message.getDeletedAt().toString());
    }
}
