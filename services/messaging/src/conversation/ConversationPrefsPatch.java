package conversation;

/**
 * The fields of a prefs change. A null field is left as it is.
 */
public record ConversationPrefsPatch(Boolean pinned, Boolean muted, Boolean archived) {
    public boolean isEmpty() {
        return pinned == null && muted == null && archived == null;
    }
}
