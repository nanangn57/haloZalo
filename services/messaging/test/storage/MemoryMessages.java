package storage;

import message.Message;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemoryMessages implements MessageRepository {
    private final Map<String, Message> rows = new LinkedHashMap<>();

    @Override
    public boolean insert(Message message) {
        if (findBySenderAndClientMsgId(message.getSenderId(), message.getClientMsgId()) != null) {
            return false;
        }
        rows.put(message.getMessageId(), message);
        return true;
    }

    @Override
    public void update(Message message) {
        rows.put(message.getMessageId(), message);
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
