package storage;

import conversation.Conversation;
import conversation.ConversationCursor;

import java.util.List;

public interface ConversationRepository {
    /**
     * Stores the conversation and its members together. Returns false when the id already exists, or when a
     * DIRECT conversation already exists for the same pair of users.
     */
    boolean insert(Conversation conversation);

    Conversation findById(String conversationId);

    /**
     * The DIRECT conversation for a pair, by {@link Conversation#directKey}, or null.
     */
    Conversation findDirect(String directKey);

    /**
     * Conversations the user is a member of, most recent activity first, starting after the cursor when given.
     */
    List<Conversation> findForMember(String userId, ConversationCursor after, int limit);
}
