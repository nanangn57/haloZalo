package storage;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationFilter;
import conversation.ConversationType;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryConversations implements ConversationRepository {
    private static final Comparator<Conversation> NEWEST_FIRST = Comparator
        .comparing(Conversation::getActivityAt)
        .thenComparing(Conversation::getConversationId)
        .reversed();

    private final Map<String, Conversation> rows = new ConcurrentHashMap<>();
    private final MemoryPrefs prefs;

    public MemoryConversations() {
        this(new MemoryPrefs());
    }

    /**
     * Reads the prefs of the same user to order and filter the list, as the Postgres query joins them.
     */
    public MemoryConversations(MemoryPrefs prefs) {
        this.prefs = prefs;
    }

    public MemoryPrefs prefs() {
        return prefs;
    }

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
    public synchronized boolean acceptRequest(String conversationId, String userId) {
        Conversation row = rows.get(conversationId);
        if (row == null || !row.isPendingFor(userId)) {
            return false;
        }
        rows.put(conversationId, row.accepted());
        return true;
    }

    @Override
    public List<Conversation> findForMember(
        String userId, Collection<String> groupIds, ConversationFilter filter, ConversationCursor after, int limit) {
        Comparator<Conversation> order = Comparator
            .comparing((Conversation row) -> prefs.of(userId, row.getConversationId()).pinned())
            .thenComparing(Conversation::getActivityAt)
            .thenComparing(Conversation::getConversationId)
            .reversed();
        return rows.values().stream()
            .filter(row -> row.getType() == ConversationType.DIRECT
                ? row.hasMember(userId) && row.isPendingFor(userId) == filter.requests()
                : !filter.requests() && groupIds.contains(row.getConversationId()))
            .filter(row -> prefs.of(userId, row.getConversationId()).archived() == filter.archived())
            .sorted(order)
            .filter(row -> after == null || isAfter(userId, row, after))
            .limit(limit)
            .toList();
    }

    /**
     * What Postgres does in the append transaction: bump last_seq and last_message_at.
     */
    void touch(String conversationId, long seq, Instant at) {
        rows.computeIfPresent(conversationId, (id, old) -> new Conversation(
            id, old.getType(), old.getMemberIds(), seq, old.getCreatedAt(), at, old.getPendingFor()));
    }

    /**
     * Strictly after the cursor in (pinned DESC, activity DESC, id DESC) order.
     */
    private boolean isAfter(String userId, Conversation row, ConversationCursor cursor) {
        boolean pinned = prefs.of(userId, row.getConversationId()).pinned();
        if (pinned != cursor.pinned()) {
            return !pinned;
        }
        int byActivity = row.getActivityAt().compareTo(cursor.activityAt());
        if (byActivity != 0) {
            return byActivity < 0;
        }
        return row.getConversationId().compareTo(cursor.conversationId()) < 0;
    }
}
