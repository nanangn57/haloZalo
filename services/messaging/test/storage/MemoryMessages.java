package storage;

import message.Message;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemoryMessages implements MessageRepository {
    private final Map<String, Message> rows = new LinkedHashMap<>();
    private final Map<String, Long> lastSeq = new HashMap<>();

    @Override
    public synchronized Message append(Message draft) {
        if (findBySenderAndClientMsgId(draft.getSenderId(), draft.getClientMsgId()) != null) {
            return null;
        }
        Message message = draft.withSeq(lastSeq.merge(draft.getConversationId(), 1L, Long::sum));
        rows.put(message.getMessageId(), message);
        return message;
    }

    @Override
    public void markDeleted(Message deleted) {
        rows.put(deleted.getMessageId(), deleted);
    }

    @Override
    public Message findById(String messageId) {
        return rows.get(messageId);
    }

    @Override
    public Message findBySenderAndClientMsgId(String senderId, String clientMsgId) {
        return rows.values().stream()
            .filter(row -> row.getSenderId().equals(senderId) && row.getClientMsgId().equals(clientMsgId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<Message> findAfterSeq(String conversationId, long afterSeq, int limit) {
        return rows.values().stream()
            .filter(row -> row.getConversationId().equals(conversationId) && row.getSeq() > afterSeq)
            .sorted(Comparator.comparingLong(Message::getSeq))
            .limit(limit)
            .toList();
    }

    public int size() {
        return rows.size();
    }
}
