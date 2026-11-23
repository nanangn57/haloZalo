package api;

import message.Message;
import message.MessageContent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The contract's Message: MessageBase plus type and body. senderId is absent on SYSTEM messages.
 * replyTo, forwardedFrom, status and deletedAt are left out when null, so a plain message keeps the exact
 * contract shape. A deleted message keeps its seq slot but never shows its content again.
 */
public final class MessageResponse {
    private final String messageId;
    private final String conversationId;
    private final long seq;
    private final String senderId;
    private final String type;
    private final String body;
    private final String clientMsgId;
    private final String replyTo;
    private final String forwardedFrom;
    private final String status;
    private final String deletedAt;
    private final String createdAt;

    public MessageResponse(Message message) {
        this.messageId = message.getMessageId();
        this.conversationId = message.getConversationId();
        this.seq = message.getSeq();
        this.senderId = message.getSenderId();
        this.type = message.getType().name();
        this.body = message.isDeleted() ? null : body(message.getContent());
        this.clientMsgId = message.getClientMsgId();
        this.replyTo = message.getReplyTo();
        this.forwardedFrom = message.getForwardedFrom();
        this.status = message.isDeleted() ? message.getStatus().name() : null;
        this.deletedAt = message.isDeleted() ? message.getDeletedAt().toString() : null;
        this.createdAt = message.getCreatedAt().toString();
    }

    private static String body(MessageContent content) {
        if (content instanceof MessageContent.Text text) {
            return text.text();
        }
        if (content instanceof MessageContent.Emotion emotion) {
            return emotion.code();
        }
        if (content instanceof MessageContent.Image image) {
            return image.url();
        }
        if (content instanceof MessageContent.Video video) {
            return video.url();
        }
        if (content instanceof MessageContent.Document document) {
            return document.url();
        }
        if (content instanceof MessageContent.Audio audio) {
            return audio.url();
        }
        if (content instanceof MessageContent.System system) {
            return system.text();
        }
        throw new IllegalStateException("No contract shape for message type " + content.type());
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSenderId() {
        return senderId;
    }

    public String getType() {
        return type;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getBody() {
        return body;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getReplyTo() {
        return replyTo;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getForwardedFrom() {
        return forwardedFrom;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getStatus() {
        return status;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDeletedAt() {
        return deletedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
