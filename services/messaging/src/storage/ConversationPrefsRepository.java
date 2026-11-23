package storage;

import conversation.ConversationPrefs;
import conversation.ConversationPrefsPatch;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public interface ConversationPrefsRepository {
    /**
     * Applies the non-null fields of the patch to the user's prefs of the conversation, creating them when absent,
     * and returns the prefs after the change.
     */
    ConversationPrefs update(String userId, String conversationId, ConversationPrefsPatch patch, Instant at);

    /**
     * The user's prefs of each given conversation. Conversations without a stored row are absent: they have the
     * defaults.
     */
    Map<String, ConversationPrefs> find(String userId, Collection<String> conversationIds);
}
