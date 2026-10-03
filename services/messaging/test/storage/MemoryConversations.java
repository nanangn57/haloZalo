package storage;

import conversation.Conversation;

import java.util.HashMap;
import java.util.Map;

public final class MemoryConversations implements ConversationRepository {
    private final Map<String, Conversation> rows = new HashMap<>();

    @Override
    public boolean insert(Conversation conversation) {
        return rows.putIfAbsent(conversation.getConversationId(), conversation) == null;
    }

    @Override
    public Conversation findById(String conversationId) {
        return rows.get(conversationId);
    }

    @Override
    public long nextSeq(String conversationId) {
        Conversation old = rows.get(conversationId);
        Conversation next = new Conversation(old.getConversationId(), old.getType(), old.getMemberIds(),
            old.getLastSeq() + 1, old.getCreatedAt());
        rows.put(conversationId, next);
        return next.getLastSeq();
    }
}
