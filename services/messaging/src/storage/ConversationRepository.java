package storage;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationFilter;

import java.util.Collection;
import java.util.List;

public interface ConversationRepository {
    /**
     * Stores the conversation, and for DIRECT its two members, together. Returns false when the id already exists,
     * or when a DIRECT conversation already exists for the same pair of users.
     */
    boolean insert(Conversation conversation);

    Conversation findById(String conversationId);

    /**
     * The DIRECT conversation for a pair, by {@link Conversation#directKey}, or null.
     */
    Conversation findDirect(String directKey);

    /**
     * The user's DIRECT conversations plus the GROUP conversations with the given ids, pinned (by this user) first,
     * then most recent activity first, starting after the cursor when given. Group ids come from identity, see
     * member.GroupMembers. The filter picks archived or not, and stranger requests (DIRECT only) or not.
     */
    List<Conversation> findForMember(
        String userId, Collection<String> groupIds, ConversationFilter filter, ConversationCursor after, int limit);

    /**
     * Ends the user's PENDING side of a DIRECT conversation. Returns false when it was not pending.
     */
    boolean acceptRequest(String conversationId, String userId);
}
