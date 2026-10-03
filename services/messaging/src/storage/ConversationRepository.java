package storage;

import conversation.Conversation;

public interface ConversationRepository {
    /**
     * Stores the conversation and its members together. Returns false when the id already exists.
     */
    boolean insert(Conversation conversation);

    Conversation findById(String conversationId);
}
