package api;

import message.Message;
import message.MessageContent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The contract's Message: MessageBase plus type and body. Only TEXT and EMOTION have a contract shape yet.
 * replyTo and forwardedFrom are left out when null, so a plain message keeps the exact contract shape.
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
    private final String createdAt;

    public MessageResponse(Message message) {
        this.messageId = message.getMessageId();
        this.conversationId = message.getConversationId();
        this.seq = message.getSeq();
        this.senderId = message.getSenderId();
        this.type = message.getType().name();
        this.body = body(message.getContent());
        this.clientMsgId = message.getClientMsgId();
        this.replyTo = message.getReplyTo();
        this.forwardedFrom = message.getForwardedFrom();
        this.createdAt = message.getCreatedAt().toString();
    }

    private static String body(MessageContent content) {
        if (content instanceof MessageContent.Text text) {
            return text.text();
        }
        if (content instanceof MessageContent.Emotion emotion) {
            return emotion.code();
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

    public String getSenderId() {
        return senderId;
    }

    public String getType() {
        return type;
    }

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

    public String getCreatedAt() {
        return createdAt;
    }
}
