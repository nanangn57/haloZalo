package conversation;

/**
 * One user's own view settings of a conversation. Nothing here is shared with the other members.
 */
public record ConversationPrefs(boolean pinned, boolean muted, boolean archived) {
    public static final ConversationPrefs DEFAULT = new ConversationPrefs(false, false, false);
}
