package storage;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationFilter;
import conversation.ConversationType;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class PostgresConversationRepository implements ConversationRepository {
    private static final String COLUMNS = "c.id, c.type, c.last_seq, c.created_at, c.last_message_at";
    private static final String OPEN = "OPEN";
    private static final String PENDING = "PENDING";
    private static final String ACTIVITY = "COALESCE(c.last_message_at, c.created_at)";
    private static final String PINNED_RANK = "(CASE WHEN COALESCE(p.pinned, FALSE) THEN 1 ELSE 0 END)";

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
                .map(member -> new Object[] {
                    Sql.uuid(conversation.getConversationId()),
                    Sql.uuid(member),
                    conversation.isPendingFor(member) ? PENDING : OPEN})
                .toList();
            jdbc.batchUpdate(
                "INSERT INTO direct_members (conversation_id, user_id, request_status) VALUES (?, ?, ?)", members);
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
    public boolean acceptRequest(String conversationId, String userId) {
        return jdbc.update(
            "UPDATE direct_members SET request_status = 'OPEN' "
                + "WHERE conversation_id = ? AND user_id = ? AND request_status = 'PENDING'",
            Sql.uuid(conversationId), Sql.uuid(userId)) > 0;
    }

    @Override
    public List<Conversation> findForMember(
        String userId, Collection<String> groupIds, ConversationFilter filter, ConversationCursor after, int limit) {
        List<Object> args = new ArrayList<>();
        // The caller's own prefs row, if any: no row means not pinned and not archived.
        args.add(Sql.uuid(userId));
        String mine = "(c.type = 'DIRECT' AND EXISTS (SELECT 1 FROM direct_members d "
            + "WHERE d.conversation_id = c.id AND d.user_id = ? AND d.request_status = ?))";
        args.add(Sql.uuid(userId));
        args.add(filter.requests() ? PENDING : OPEN);
        // Groups have no request side, so they only belong to the normal inbox.
        if (!filter.requests() && !groupIds.isEmpty()) {
            mine = "(" + mine + " OR (c.type = 'GROUP' AND c.id IN ("
                + String.join(", ", Collections.nCopies(groupIds.size(), "?")) + ")))";
            groupIds.forEach(groupId -> args.add(Sql.uuid(groupId)));
        }
        String archived = " AND COALESCE(p.archived, FALSE) = ?";
        args.add(filter.archived());
        String page = "";
        if (after != null) {
            // Row comparison: strictly after the cursor in (pinned DESC, activity DESC, id DESC) order.
            page = " AND (" + PINNED_RANK + ", " + ACTIVITY + ", c.id) < (?, ?, ?)";
            args.add(after.pinned() ? 1 : 0);
            args.add(Sql.time(after.activityAt()));
            args.add(Sql.uuid(after.conversationId()));
        }
        args.add(limit);
        List<Conversation> rows = jdbc.query(
            "SELECT " + COLUMNS + " FROM conversations c "
                + "LEFT JOIN conversation_prefs p ON p.conversation_id = c.id AND p.user_id = ? "
                + "WHERE " + mine + archived + page
                + " ORDER BY " + PINNED_RANK + " DESC, " + ACTIVITY + " DESC, c.id DESC LIMIT ?",
            CONVERSATION, args.toArray());
        return withMembers(rows);
    }

    private Conversation one(List<Conversation> rows) {
        Conversation row = DataAccessUtils.singleResult(rows);
        return row == null ? null : withMembers(List.of(row)).get(0);
    }

    /**
     * Loads the members of all given DIRECT conversations in one query. GROUP conversations have none stored.
     */
    private List<Conversation> withMembers(List<Conversation> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        String placeholders = String.join(", ", Collections.nCopies(rows.size(), "?"));
        Object[] ids = rows.stream().map(row -> Sql.uuid(row.getConversationId())).toArray();
        Map<String, Set<String>> members = new HashMap<>();
        Map<String, String> pending = new HashMap<>();
        jdbc.query(
            "SELECT conversation_id::text, user_id::text, request_status FROM direct_members "
                + "WHERE conversation_id IN (" + placeholders + ")",
            (resultSet) -> {
                members.computeIfAbsent(resultSet.getString(1), key -> new HashSet<>()).add(resultSet.getString(2));
                if (PENDING.equals(resultSet.getString(3))) {
                    pending.put(resultSet.getString(1), resultSet.getString(2));
                }
            },
            ids);
        return rows.stream()
            .map(row -> new Conversation(row.getConversationId(), row.getType(),
                members.getOrDefault(row.getConversationId(), Set.of()), row.getLastSeq(), row.getCreatedAt(),
                row.getLastMessageAt(), pending.get(row.getConversationId())))
            .toList();
    }
}
