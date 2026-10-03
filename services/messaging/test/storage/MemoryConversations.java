package storage;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationType;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryConversations implements ConversationRepository {
    private static final Comparator<Conversation> NEWEST_FIRST = Comparator
        .comparing(Conversation::getActivityAt)
        .thenComparing(Conversation::getConversationId)
        .reversed();

    private final Map<String, Conversation> rows = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean insert(Conversation conversation) {
        String key = conversation.getDirectKey();
        if (rows.containsKey(conversation.getConversationId()) || (key != null && findDirect(key) != null)) {
            return false;
        }
        rows.put(conversation.getConversationId(), conversation);
        return true;
    }

    @Override
    public Conversation findById(String conversationId) {
        return rows.get(conversationId);
    }

    @Override
    public Conversation findDirect(String directKey) {
        return rows.values().stream().filter(row -> directKey.equals(row.getDirectKey())).findFirst().orElse(null);
    }

    @Override
    public List<Conversation> findForMember(String userId, ConversationCursor after, int limit) {
        return rows.values().stream()
            .filter(row -> row.hasMember(userId))
            .filter(row -> after == null || NEWEST_FIRST.compare(row, cursorRow(after)) > 0)
            .sorted(NEWEST_FIRST)
            .limit(limit)
            .toList();
    }

    /**
     * What Postgres does in the append transaction: bump last_seq and last_message_at.
     */
    void touch(String conversationId, long seq, Instant at) {
        rows.computeIfPresent(conversationId, (id, old) -> new Conversation(
            id, old.getType(), old.getMemberIds(), seq, old.getCreatedAt(), at));
    }

    private static Conversation cursorRow(ConversationCursor cursor) {
        return new Conversation(cursor.conversationId(), ConversationType.GROUP, Set.of(), 0, cursor.activityAt());
    }
}
