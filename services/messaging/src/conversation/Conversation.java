package conversation;

import java.time.Instant;
import java.util.Set;

public final class Conversation {
    private final String conversationId;
    private final ConversationType type;
    private final Set<String> memberIds;
    private final long lastSeq;
    private final Instant createdAt;
    private final Instant lastMessageAt;

    public Conversation(String conversationId, ConversationType type, Set<String> memberIds, long lastSeq, Instant createdAt) {
        this(conversationId, type, memberIds, lastSeq, createdAt, null);
    }

    public Conversation(
        String conversationId,
        ConversationType type,
        Set<String> memberIds,
        long lastSeq,
        Instant createdAt,
        Instant lastMessageAt) {
        this.conversationId = conversationId;
        this.type = type;
        this.memberIds = Set.copyOf(memberIds);
        this.lastSeq = lastSeq;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
    }

    /**
     * One key per pair of users, whichever of them opened the conversation. Only DIRECT conversations have one.
     */
    public static String directKey(Set<String> memberIds) {
        return String.join(":", memberIds.stream().sorted().toList());
    }

    public String getDirectKey() {
        return type == ConversationType.DIRECT ? directKey(memberIds) : null;
    }

    /**
     * Orders the conversation list: the newest message, or the creation time before the first message.
     */
    public Instant getActivityAt() {
        return lastMessageAt != null ? lastMessageAt : createdAt;
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

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
