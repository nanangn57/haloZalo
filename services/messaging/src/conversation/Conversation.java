package conversation;

import java.time.Instant;
import java.util.Set;

/**
 * memberIds holds the two people of a DIRECT conversation. It is empty for GROUP: group members belong to identity,
 * see member.ConversationMembers.
 */
public final class Conversation {
    private final String conversationId;
    private final ConversationType type;
    private final Set<String> memberIds;
    private final long lastSeq;
    private final Instant createdAt;
    private final Instant lastMessageAt;
    private final String pendingFor;

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
        this(conversationId, type, memberIds, lastSeq, createdAt, lastMessageAt, null);
    }

    /**
     * pendingFor is the member of a DIRECT conversation whose side is still a stranger's request, or null.
     */
    public Conversation(
        String conversationId,
        ConversationType type,
        Set<String> memberIds,
        long lastSeq,
        Instant createdAt,
        Instant lastMessageAt,
        String pendingFor) {
        this.conversationId = conversationId;
        this.type = type;
        this.memberIds = Set.copyOf(memberIds);
        this.lastSeq = lastSeq;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
        this.pendingFor = pendingFor;
    }

    /**
     * The same conversation with the request accepted.
     */
    public Conversation accepted() {
        return new Conversation(conversationId, type, memberIds, lastSeq, createdAt, lastMessageAt, null);
    }

    public boolean isPendingFor(String userId) {
        return pendingFor != null && pendingFor.equals(userId);
    }

    public String getPendingFor() {
        return pendingFor;
    }

    /**
     * DIRECT only: the member who is not userId, or null when userId is not a member.
     */
    public String otherMember(String userId) {
        if (!memberIds.contains(userId)) {
            return null;
        }
        return memberIds.stream().filter(member -> !member.equals(userId)).findFirst().orElse(null);
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

    /**
     * DIRECT only. Use member.ConversationMembers to ask about any conversation.
     */
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
