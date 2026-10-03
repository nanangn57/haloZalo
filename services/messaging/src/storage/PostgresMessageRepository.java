package storage;

import message.Message;
import message.MessageMetadata;
import message.MessageStatus;
import message.MessageType;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
public final class PostgresMessageRepository implements MessageRepository {
    private static final String COLUMNS = "id, conversation_id, seq, sender_id, client_msg_id, type, content, metadata, "
        + "reply_to, forwarded_from, status, created_at, updated_at, deleted_at";

    private static final RowMapper<Message> MESSAGE = (row, index) -> {
        MessageType type = MessageType.valueOf(row.getString("type"));
        String metadata = row.getString("metadata");
        return new Message(
            row.getString("id"),
            row.getString("conversation_id"),
            row.getLong("seq"),
            row.getString("sender_id"),
            row.getString("client_msg_id"),
            MessageContentJson.content(type, row.getString("content")),
            metadata == null ? null : new MessageMetadata(MessageContentJson.metadata(metadata)),
            row.getString("reply_to"),
            row.getString("forwarded_from"),
            MessageStatus.valueOf(row.getString("status")),
            Sql.instant(row, "created_at"),
            Sql.instant(row, "updated_at"),
            Sql.instant(row, "deleted_at"));
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PostgresMessageRepository(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /**
     * The UPDATE locks the conversation row, so sends to one conversation get seq one after another. A duplicate
     * clientMsgId rolls the whole transaction back, so its seq is never used and seq has no holes.
     */
    @Override
    public Message append(Message draft) {
        return transaction.execute(status -> {
            Long seq = jdbc.query(
                "UPDATE conversations SET last_seq = last_seq + 1 WHERE id = ? RETURNING last_seq",
                rows -> rows.next() ? rows.getLong(1) : null,
                Sql.uuid(draft.getConversationId()));
            if (seq == null) {
                throw new IllegalStateException("Conversation not found: " + draft.getConversationId());
            }
            Message message = draft.withSeq(seq);
            int rows = jdbc.update(
                "INSERT INTO messages (" + COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (sender_id, client_msg_id) DO NOTHING",
                Sql.uuid(message.getMessageId()),
                Sql.uuid(message.getConversationId()),
                message.getSeq(),
                Sql.uuid(message.getSenderId()),
                Sql.uuid(message.getClientMsgId()),
                message.getType().name(),
                Sql.jsonb(MessageContentJson.write(message.getContent())),
                Sql.jsonb(message.getMetadata() == null ? null : MessageContentJson.write(message.getMetadata().getValues())),
                Sql.uuid(message.getReplyTo()),
                Sql.uuid(message.getForwardedFrom()),
                message.getStatus().name(),
                Sql.time(message.getCreatedAt()),
                Sql.time(message.getUpdatedAt()),
                Sql.time(message.getDeletedAt()));
            if (rows == 0) {
                status.setRollbackOnly();
                return null;
            }
            return message;
        });
    }

    @Override
    public void markDeleted(Message deleted) {
        jdbc.update("UPDATE messages SET status = ?, updated_at = ?, deleted_at = ? WHERE id = ?",
            deleted.getStatus().name(),
            Sql.time(deleted.getUpdatedAt()),
            Sql.time(deleted.getDeletedAt()),
            Sql.uuid(deleted.getMessageId()));
    }

    @Override
    public Message findById(String messageId) {
        return DataAccessUtils.singleResult(jdbc.query(
            "SELECT " + COLUMNS + " FROM messages WHERE id = ?", MESSAGE, Sql.uuid(messageId)));
    }

    @Override
    public Message findBySenderAndClientMsgId(String senderId, String clientMsgId) {
        return DataAccessUtils.singleResult(jdbc.query(
            "SELECT " + COLUMNS + " FROM messages WHERE sender_id = ? AND client_msg_id = ?",
            MESSAGE, Sql.uuid(senderId), Sql.uuid(clientMsgId)));
    }

    @Override
    public List<Message> findAfterSeq(String conversationId, long afterSeq, int limit) {
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM messages WHERE conversation_id = ? AND seq > ? ORDER BY seq LIMIT ?",
            MESSAGE, Sql.uuid(conversationId), afterSeq, limit);
    }
}
