package storage;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationType;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class PostgresConversationRepository implements ConversationRepository {
    private static final String COLUMNS = "c.id, c.type, c.last_seq, c.created_at, c.last_message_at";
    private static final String ACTIVITY = "COALESCE(c.last_message_at, c.created_at)";

    /**
     * Members are filled in afterwards, see {@link #withMembers}.
     */
    private static final RowMapper<Conversation> CONVERSATION = (rows, index) -> new Conversation(
        rows.getString("id"),
        ConversationType.valueOf(rows.getString("type")),
        Set.of(),
        rows.getLong("last_seq"),
        Sql.instant(rows, "created_at"),
        Sql.instant(rows, "last_message_at"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PostgresConversationRepository(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public boolean insert(Conversation conversation) {
        Boolean inserted = transaction.execute(status -> {
            // No conflict target: a taken id and a taken DIRECT pair both mean "not inserted".
            int rows = jdbc.update(
                "INSERT INTO conversations (id, type, last_seq, created_at, direct_key) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT DO NOTHING",
                Sql.uuid(conversation.getConversationId()),
                conversation.getType().name(),
                conversation.getLastSeq(),
                Sql.time(conversation.getCreatedAt()),
                conversation.getDirectKey());
            if (rows == 0) {
                return false;
            }
            List<Object[]> members = conversation.getMemberIds().stream()
                .map(member -> new Object[] {Sql.uuid(conversation.getConversationId()), Sql.uuid(member)})
                .toList();
            jdbc.batchUpdate("INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)", members);
            return true;
        });
        return Boolean.TRUE.equals(inserted);
    }

    @Override
    public Conversation findById(String conversationId) {
        return one(jdbc.query("SELECT " + COLUMNS + " FROM conversations c WHERE c.id = ?",
            CONVERSATION, Sql.uuid(conversationId)));
    }

    @Override
    public Conversation findDirect(String directKey) {
        return one(jdbc.query("SELECT " + COLUMNS + " FROM conversations c WHERE c.direct_key = ?",
            CONVERSATION, directKey));
    }

    @Override
    public List<Conversation> findForMember(String userId, ConversationCursor after, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(Sql.uuid(userId));
        String page = "";
        if (after != null) {
            // Row comparison: strictly after the cursor in (activity DESC, id DESC) order.
            page = " AND (" + ACTIVITY + ", c.id) < (?, ?)";
            args.add(Sql.time(after.activityAt()));
            args.add(Sql.uuid(after.conversationId()));
        }
        args.add(limit);
        List<Conversation> rows = jdbc.query(
            "SELECT " + COLUMNS + " FROM conversations c "
                + "JOIN conversation_members m ON m.conversation_id = c.id "
                + "WHERE m.user_id = ?" + page
                + " ORDER BY " + ACTIVITY + " DESC, c.id DESC LIMIT ?",
            CONVERSATION, args.toArray());
        return withMembers(rows);
    }

    private Conversation one(List<Conversation> rows) {
        Conversation row = DataAccessUtils.singleResult(rows);
        return row == null ? null : withMembers(List.of(row)).get(0);
    }

    /**
     * Loads the members of all given conversations in one query.
     */
    private List<Conversation> withMembers(List<Conversation> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        String placeholders = String.join(", ", Collections.nCopies(rows.size(), "?"));
        Object[] ids = rows.stream().map(row -> Sql.uuid(row.getConversationId())).toArray();
        Map<String, Set<String>> members = new HashMap<>();
        jdbc.query(
            "SELECT conversation_id::text, user_id::text FROM conversation_members WHERE conversation_id IN (" + placeholders + ")",
            (resultSet) -> {
                members.computeIfAbsent(resultSet.getString(1), key -> new HashSet<>()).add(resultSet.getString(2));
            },
            ids);
        return rows.stream()
            .map(row -> new Conversation(row.getConversationId(), row.getType(),
                members.getOrDefault(row.getConversationId(), Set.of()), row.getLastSeq(), row.getCreatedAt(),
                row.getLastMessageAt()))
            .toList();
    }
}
