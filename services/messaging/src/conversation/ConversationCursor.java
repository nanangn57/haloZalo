package conversation;

import java.time.Instant;

/**
 * Where the next page of the conversation list starts: after this activity time and id, newest first.
 * Written as "<epoch millis>_<conversationId>" so clients pass it back unchanged.
 */
public record ConversationCursor(Instant activityAt, String conversationId) {
    public static ConversationCursor after(Conversation conversation) {
        return new ConversationCursor(conversation.getActivityAt(), conversation.getConversationId());
    }

    public String encode() {
        return activityAt.toEpochMilli() + "_" + conversationId;
    }
}
