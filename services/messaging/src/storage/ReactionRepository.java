package storage;

import reaction.MessageReaction;

import java.util.List;

public interface ReactionRepository {
    /**
     * Returns false when the user already reacted to the message with the same code.
     */
    boolean insert(MessageReaction reaction);

    boolean delete(String messageId, String userId, String code);

    List<MessageReaction> findByMessageId(String messageId);
}
