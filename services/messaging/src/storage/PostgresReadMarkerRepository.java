package storage;

import conversation.ReadState;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public final class PostgresReadMarkerRepository implements ReadMarkerRepository {
    private final JdbcTemplate jdbc;

    public PostgresReadMarkerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Advance advance(String conversationId, String userId, long seq, Instant at) {
        if (seq > 0) {
            // The WHERE on the update keeps the marker from moving back. RETURNING gives a row only when it moved.
            List<Long> moved = jdbc.queryForList(
                "INSERT INTO read_markers (conversation_id, user_id, last_read_seq, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (conversation_id, user_id) DO UPDATE "
                    + "SET last_read_seq = EXCLUDED.last_read_seq, updated_at = EXCLUDED.updated_at "
                    + "WHERE read_markers.last_read_seq < EXCLUDED.last_read_seq "
                    + "RETURNING last_read_seq",
                Long.class,
                Sql.uuid(conversationId), Sql.uuid(userId), seq, Sql.time(at));
            if (!moved.isEmpty()) {
                return new Advance(moved.get(0), true);
            }
        }
        List<Long> current = jdbc.queryForList(
            "SELECT last_read_seq FROM read_markers WHERE conversation_id = ? AND user_id = ?",
            Long.class, Sql.uuid(conversationId), Sql.uuid(userId));
        return new Advance(current.isEmpty() ? 0 : current.get(0), false);
    }

    @Override
    public Map<String, ReadState> readStates(String userId, Collection<String> conversationIds) {
        Map<String, ReadState> states = new HashMap<>();
        if (conversationIds.isEmpty()) {
            return states;
        }
        List<Object> args = new ArrayList<>();
        args.add(Sql.uuid(userId));
        args.add(Sql.uuid(userId));
        conversationIds.forEach(id -> args.add(Sql.uuid(id)));
        // Each count walks the (conversation_id, seq) index from the marker onwards.
        jdbc.query(
            "SELECT c.id::text AS id, COALESCE(r.last_read_seq, 0) AS last_read, "
                + "(SELECT count(*) FROM messages m WHERE m.conversation_id = c.id "
                + "AND m.seq > COALESCE(r.last_read_seq, 0) AND m.sender_id <> ? AND m.status <> 'DELETED') AS unread "
                + "FROM conversations c LEFT JOIN read_markers r ON r.conversation_id = c.id AND r.user_id = ? "
                + "WHERE c.id IN (" + String.join(", ", Collections.nCopies(conversationIds.size(), "?")) + ")",
            row -> {
                states.put(row.getString("id"), new ReadState(row.getLong("last_read"), row.getLong("unread")));
            },
            args.toArray());
        return states;
    }
}
