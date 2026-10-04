package reaction;

import java.time.Instant;

/**
 * One user's reaction on one message. A user may react with several codes, but each code once.
 */
public final class MessageReaction {
    private final String reactionId;
    private final String messageId;
    private final String userId;
    private final String code;
    private final Instant createdAt;

    public MessageReaction(String reactionId, String messageId, String userId, String code, Instant createdAt) {
        this.reactionId = reactionId;
        this.messageId = messageId;
        this.userId = userId;
        this.code = code;
        this.createdAt = createdAt;
    }

    public String getReactionId() {
        return reactionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
