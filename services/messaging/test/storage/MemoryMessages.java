package storage;

import message.Message;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class MemoryMessages implements MessageRepository, MessageSearchRepository {
    private final Map<String, Message> rows = new LinkedHashMap<>();
    private final Map<String, Long> lastSeq = new HashMap<>();
    private final MemoryConversations conversations;

    public MemoryMessages() {
        this(null);
    }

    /**
     * Linked to the conversations so appends move last_seq and last_message_at, as in Postgres.
     */
    public MemoryMessages(MemoryConversations conversations) {
        this.conversations = conversations;
    }

    @Override
    public synchronized Message append(Message draft) {
        if (findBySenderAndClientMsgId(draft.getSenderId(), draft.getClientMsgId()) != null) {
            return null;
        }
        Message message = draft.withSeq(lastSeq.merge(draft.getConversationId(), 1L, Long::sum));
        rows.put(message.getMessageId(), message);
        if (conversations != null) {
            conversations.touch(message.getConversationId(), message.getSeq(), message.getCreatedAt());
        }
        return message;
    }

    @Override
    public synchronized void markDeleted(Message deleted) {
        rows.put(deleted.getMessageId(), deleted);
    }

    @Override
    public synchronized Message findById(String messageId) {
        return rows.get(messageId);
    }

    @Override
    public synchronized Message findBySenderAndClientMsgId(String senderId, String clientMsgId) {
        return rows.values().stream()
            .filter(row -> Objects.equals(row.getSenderId(), senderId) && row.getClientMsgId().equals(clientMsgId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public synchronized List<Message> findAfterSeq(String conversationId, long afterSeq, int limit) {
        return inConversation(conversationId)
            .filter(row -> row.getSeq() > afterSeq)
            .limit(limit)
            .toList();
    }

    @Override
    public synchronized List<Message> findBeforeSeq(String conversationId, long beforeSeq, int limit) {
        List<Message> older = inConversation(conversationId).filter(row -> row.getSeq() < beforeSeq).toList();
        return older.subList(Math.max(0, older.size() - limit), older.size());
    }

    @Override
    public synchronized Map<String, Message> findLatest(Collection<String> conversationIds) {
        Map<String, Message> latest = new HashMap<>();
        for (String id : conversationIds) {
            List<Message> all = inConversation(id).toList();
            if (!all.isEmpty()) {
                latest.put(id, all.get(all.size() - 1));
            }
        }
        return latest;
    }

    @Override
    public synchronized List<Message> searchAfterSeq(String conversationId, long afterSeq, String text, int limit) {
        return inConversation(conversationId)
            .filter(row -> row.getSeq() > afterSeq)
            .filter(row -> matches(row, text))
            .limit(limit)
            .toList();
    }

    @Override
    public synchronized List<Message> searchBeforeSeq(String conversationId, long beforeSeq, String text, int limit) {
        List<Message> older = inConversation(conversationId)
            .filter(row -> row.getSeq() < beforeSeq)
            .filter(row -> matches(row, text))
            .toList();
        return older.subList(Math.max(0, older.size() - limit), older.size());
    }

    private static boolean matches(Message row, String text) {
        return !row.isDeleted()
            && row.getContent() instanceof message.MessageContent.Text body
            && body.text().toLowerCase().contains(text.toLowerCase());
    }

    public synchronized int size() {
        return rows.size();
    }

    private Stream<Message> inConversation(String conversationId) {
        return rows.values().stream()
            .filter(row -> row.getConversationId().equals(conversationId))
            .sorted(Comparator.comparingLong(Message::getSeq));
    }
}
