package storage;

import conversation.Conversation;

public interface ConversationRepository {
    /**
     * Returns false when a conversation with the same id already exists.
     */
    boolean insert(Conversation conversation);

    Conversation findById(String conversationId);

    /**
     * Atomically increments and returns the conversation's last seq. The first message gets 1.
     */
    long nextSeq(String conversationId);
}
