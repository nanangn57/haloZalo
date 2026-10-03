package storage;

import conversation.Conversation;
import conversation.ConversationType;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;

@Component
public final class PostgresConversationRepository implements ConversationRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PostgresConversationRepository(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public boolean insert(Conversation conversation) {
        Boolean inserted = transaction.execute(status -> {
            int rows = jdbc.update(
                "INSERT INTO conversations (id, type, last_seq, created_at) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                Sql.uuid(conversation.getConversationId()),
                conversation.getType().name(),
                conversation.getLastSeq(),
                Sql.time(conversation.getCreatedAt()));
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
        Conversation row = DataAccessUtils.singleResult(jdbc.query(
            "SELECT id, type, last_seq, created_at FROM conversations WHERE id = ?",
            (rows, index) -> new Conversation(
                rows.getString("id"),
                ConversationType.valueOf(rows.getString("type")),
                new HashSet<>(),
                rows.getLong("last_seq"),
                Sql.instant(rows, "created_at")),
            Sql.uuid(conversationId)));
        if (row == null) {
            return null;
        }
        List<String> members = jdbc.queryForList(
            "SELECT user_id::text FROM conversation_members WHERE conversation_id = ?", String.class, Sql.uuid(conversationId));
        return new Conversation(row.getConversationId(), row.getType(), new HashSet<>(members), row.getLastSeq(), row.getCreatedAt());
    }
}
