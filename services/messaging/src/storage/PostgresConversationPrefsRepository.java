package storage;

import conversation.ConversationPrefs;
import conversation.ConversationPrefsPatch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class PostgresConversationPrefsRepository implements ConversationPrefsRepository, MutedConversations {
    private final JdbcTemplate jdbc;

    public PostgresConversationPrefsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConversationPrefs update(String userId, String conversationId, ConversationPrefsPatch patch, Instant at) {
        // A missing row starts from false; an existing row keeps every field the patch leaves null.
        return jdbc.query(
            "INSERT INTO conversation_prefs (user_id, conversation_id, pinned, muted, archived, updated_at) "
                + "VALUES (?, ?, COALESCE(CAST(? AS boolean), FALSE), COALESCE(CAST(? AS boolean), FALSE), "
                + "COALESCE(CAST(? AS boolean), FALSE), ?) "
                + "ON CONFLICT (user_id, conversation_id) DO UPDATE SET "
                + "pinned = COALESCE(CAST(? AS boolean), conversation_prefs.pinned), "
                + "muted = COALESCE(CAST(? AS boolean), conversation_prefs.muted), "
                + "archived = COALESCE(CAST(? AS boolean), conversation_prefs.archived), "
                + "updated_at = EXCLUDED.updated_at "
                + "RETURNING pinned, muted, archived",
            rows -> {
                rows.next();
                return new ConversationPrefs(rows.getBoolean(1), rows.getBoolean(2), rows.getBoolean(3));
            },
            Sql.uuid(userId), Sql.uuid(conversationId),
            patch.pinned(), patch.muted(), patch.archived(),
            Sql.time(at),
            patch.pinned(), patch.muted(), patch.archived());
    }

    @Override
    public Map<String, ConversationPrefs> find(String userId, Collection<String> conversationIds) {
        Map<String, ConversationPrefs> found = new HashMap<>();
        if (conversationIds.isEmpty()) {
            return found;
        }
        String placeholders = String.join(", ", Collections.nCopies(conversationIds.size(), "?"));
        List<Object> args = new java.util.ArrayList<>();
        args.add(Sql.uuid(userId));
        conversationIds.forEach(id -> args.add(Sql.uuid(id)));
        jdbc.query(
            "SELECT conversation_id::text, pinned, muted, archived FROM conversation_prefs "
                + "WHERE user_id = ? AND conversation_id IN (" + placeholders + ")",
            rows -> {
                found.put(rows.getString(1),
                    new ConversationPrefs(rows.getBoolean(2), rows.getBoolean(3), rows.getBoolean(4)));
            },
            args.toArray());
        return found;
    }

    @Override
    public Set<String> mutedAmong(String conversationId, Collection<String> userIds) {
        Set<String> muted = new HashSet<>();
        if (userIds.isEmpty()) {
            return muted;
        }
        String placeholders = String.join(", ", Collections.nCopies(userIds.size(), "?"));
        List<Object> args = new java.util.ArrayList<>();
        args.add(Sql.uuid(conversationId));
        userIds.forEach(id -> args.add(Sql.uuid(id)));
        jdbc.query(
            "SELECT user_id::text FROM conversation_prefs WHERE conversation_id = ? AND muted "
                + "AND user_id IN (" + placeholders + ")",
            rows -> {
                muted.add(rows.getString(1));
            },
            args.toArray());
        return muted;
    }
}
