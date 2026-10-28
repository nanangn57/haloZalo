package message;

import java.time.Instant;

/**
 * One message in one conversation. Recipients come from the conversation members, so there is no receiver here.
 */
public final class Message {
    private final String messageId;
    private final String conversationId;
    private final long seq;
    private final String senderId;
    private final String clientMsgId;
    private final MessageContent content;
    private final MessageMetadata metadata;
    private final String replyTo;
    private final String forwardedFrom;
    private final MessageStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    public Message(
        String messageId,
        String conversationId,
        long seq,
        String senderId,
        String clientMsgId,
        MessageContent content,
        MessageMetadata metadata,
        String replyTo,
        String forwardedFrom,
        MessageStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.seq = seq;
        this.senderId = senderId;
        this.clientMsgId = clientMsgId;
        this.content = content;
        this.metadata = metadata;
        this.replyTo = replyTo;
        this.forwardedFrom = forwardedFrom;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public Message withSeq(long assigned) {
        return new Message(messageId, conversationId, assigned, senderId, clientMsgId, content, metadata,
            replyTo, forwardedFrom, status, createdAt, updatedAt, deletedAt);
    }

    public Message softDeleted(Instant now) {
        return new Message(messageId, conversationId, seq, senderId, clientMsgId, content, metadata,
            replyTo, forwardedFrom, MessageStatus.DELETED, createdAt, now, now);
    }

    public boolean isDeleted() {
        return status == MessageStatus.DELETED;
    }

    public MessageType getType() {
        return content.type();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getSeq() {
        return seq;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public MessageContent getContent() {
        return content;
    }

    public MessageMetadata getMetadata() {
        return metadata;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public String getForwardedFrom() {
        return forwardedFrom;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
