package storage;

import reaction.MessageReaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class PostgresReactionRepository implements ReactionRepository {
    private final JdbcTemplate jdbc;

    public PostgresReactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(MessageReaction reaction) {
        return jdbc.update(
            "INSERT INTO message_reactions (id, message_id, user_id, code, created_at) VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT (message_id, user_id, code) DO NOTHING",
            Sql.uuid(reaction.getReactionId()),
            Sql.uuid(reaction.getMessageId()),
            Sql.uuid(reaction.getUserId()),
            reaction.getCode(),
            Sql.time(reaction.getCreatedAt())) > 0;
    }

    @Override
    public boolean delete(String messageId, String userId, String code) {
        return jdbc.update("DELETE FROM message_reactions WHERE message_id = ? AND user_id = ? AND code = ?",
            Sql.uuid(messageId), Sql.uuid(userId), code) > 0;
    }

    @Override
    public List<MessageReaction> findByMessageId(String messageId) {
        return jdbc.query(
            "SELECT id, message_id, user_id, code, created_at FROM message_reactions WHERE message_id = ? ORDER BY created_at, id",
            (row, index) -> new MessageReaction(
                row.getString("id"),
                row.getString("message_id"),
                row.getString("user_id"),
                row.getString("code"),
                Sql.instant(row, "created_at")),
            Sql.uuid(messageId));
    }
}
