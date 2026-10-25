package identity.access;

public interface ConversationAccess {
    boolean canEnter(String userId, String conversationId);
}
