package conversation;

import java.time.Instant;

/**
 * Where the next page of the conversation list starts: after this pinned flag, activity time and id. Pinned come
 * first, then newest activity first.
 * Written as "<epoch millis>_<conversationId>", with a leading "p" for a pinned row, so clients pass it back unchanged.
 */
public record ConversationCursor(boolean pinned, Instant activityAt, String conversationId) {
    public ConversationCursor(Instant activityAt, String conversationId) {
        this(false, activityAt, conversationId);
    }

    public static ConversationCursor after(Conversation conversation) {
        return after(conversation, false);
    }

    public static ConversationCursor after(Conversation conversation, boolean pinned) {
        return new ConversationCursor(pinned, conversation.getActivityAt(), conversation.getConversationId());
    }

    public String encode() {
        return (pinned ? "p" : "") + activityAt.toEpochMilli() + "_" + conversationId;
    }
}
