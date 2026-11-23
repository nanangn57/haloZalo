package storage;

import java.util.Collection;
import java.util.Set;

public interface MutedConversations {
    /**
     * The users among userIds who muted the conversation.
     */
    Set<String> mutedAmong(String conversationId, Collection<String> userIds);
}
