package api;

import message.Message;
import message.MessageContent;

/**
 * The contract's Message: MessageBase plus type and body. Only TEXT and EMOTION have a contract shape yet.
 */
public final class MessageResponse {
    private final String messageId;
    private final String conversationId;
    private final long seq;
    private final String senderId;
    private final String type;
    private final String body;
    private final String clientMsgId;
    private final String createdAt;

    public MessageResponse(Message message) {
        this.messageId = message.getMessageId();
        this.conversationId = message.getConversationId();
        this.seq = message.getSeq();
        this.senderId = message.getSenderId();
        this.type = message.getType().name();
        this.body = body(message.getContent());
        this.clientMsgId = message.getClientMsgId();
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

    public String getCreatedAt() {
        return createdAt;
    }
}
