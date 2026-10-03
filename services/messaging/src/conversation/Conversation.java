package conversation;

import java.time.Instant;
import java.util.Set;

public final class Conversation {
    private final String conversationId;
    private final ConversationType type;
    private final Set<String> memberIds;
    private final long lastSeq;
    private final Instant createdAt;

    public Conversation(String conversationId, ConversationType type, Set<String> memberIds, long lastSeq, Instant createdAt) {
        this.conversationId = conversationId;
        this.type = type;
        this.memberIds = Set.copyOf(memberIds);
        this.lastSeq = lastSeq;
        this.createdAt = createdAt;
    }

    public boolean hasMember(String userId) {
        return memberIds.contains(userId);
    }

    public String getConversationId() {
        return conversationId;
    }

    public ConversationType getType() {
        return type;
    }

    public Set<String> getMemberIds() {
        return memberIds;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
