package event;

import message.Message;
import reaction.MessageReaction;

/**
 * Called only after the change is stored, and only when something actually changed.
 */
public interface EventPublisher {
    void publishMessageCreated(Message message);

    void publishMessageDeleted(Message message);

    void publishReactionAdded(String conversationId, MessageReaction reaction);

    void publishReactionRemoved(String conversationId, String messageId, String userId, String code);
}
