package storage;

import reaction.MessageReaction;

import java.util.ArrayList;
import java.util.List;

public final class MemoryReactions implements ReactionRepository {
    private final List<MessageReaction> rows = new ArrayList<>();

    @Override
    public boolean insert(MessageReaction reaction) {
        if (rows.stream().anyMatch(row -> same(row, reaction.getMessageId(), reaction.getUserId(), reaction.getCode()))) {
            return false;
        }
        rows.add(reaction);
        return true;
    }

    @Override
    public boolean delete(String messageId, String userId, String code) {
        return rows.removeIf(row -> same(row, messageId, userId, code));
    }

    @Override
    public List<MessageReaction> findByMessageId(String messageId) {
        return rows.stream().filter(row -> row.getMessageId().equals(messageId)).toList();
    }

    private static boolean same(MessageReaction row, String messageId, String userId, String code) {
        return row.getMessageId().equals(messageId) && row.getUserId().equals(userId) && row.getCode().equals(code);
    }
}
