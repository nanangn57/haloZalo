package storage;

import conversation.ConversationPrefs;
import conversation.ConversationPrefsPatch;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryPrefs implements ConversationPrefsRepository, MutedConversations {
    private final Map<String, ConversationPrefs> rows = new ConcurrentHashMap<>();

    @Override
    public synchronized ConversationPrefs update(String userId, String conversationId, ConversationPrefsPatch patch, Instant at) {
        ConversationPrefs current = rows.getOrDefault(key(userId, conversationId), ConversationPrefs.DEFAULT);
        ConversationPrefs changed = new ConversationPrefs(
            patch.pinned() != null ? patch.pinned() : current.pinned(),
            patch.muted() != null ? patch.muted() : current.muted(),
            patch.archived() != null ? patch.archived() : current.archived());
        rows.put(key(userId, conversationId), changed);
        return changed;
    }

    @Override
    public Map<String, ConversationPrefs> find(String userId, Collection<String> conversationIds) {
        Map<String, ConversationPrefs> found = new HashMap<>();
        for (String id : conversationIds) {
            ConversationPrefs row = rows.get(key(userId, id));
            if (row != null) {
                found.put(id, row);
            }
        }
        return found;
    }

    @Override
    public Set<String> mutedAmong(String conversationId, Collection<String> userIds) {
        Set<String> muted = new HashSet<>();
        for (String userId : userIds) {
            ConversationPrefs row = rows.get(key(userId, conversationId));
            if (row != null && row.muted()) {
                muted.add(userId);
            }
        }
        return muted;
    }

    ConversationPrefs of(String userId, String conversationId) {
        return rows.getOrDefault(key(userId, conversationId), ConversationPrefs.DEFAULT);
    }

    private static String key(String userId, String conversationId) {
        return userId + "|" + conversationId;
    }
}
