package conversation;

/**
 * Which part of the caller's conversations to list. requests: the stranger requests waiting for the caller, and
 * nothing else. archived: only archived ones; otherwise archived ones are left out.
 */
public record ConversationFilter(boolean requests, boolean archived) {
    public static final ConversationFilter DEFAULT = new ConversationFilter(false, false);
}
