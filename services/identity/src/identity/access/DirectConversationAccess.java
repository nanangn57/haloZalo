package identity.access;

/**
 * Membership of a DIRECT conversation. Messaging supplies this in the same process.
 * Group chats use the group id as conversationId and are answered by {@link MembershipConversationAccess}.
 */
public interface DirectConversationAccess {
    boolean isMember(String conversationId, String userId);
}
