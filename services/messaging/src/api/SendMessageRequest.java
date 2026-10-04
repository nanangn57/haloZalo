package api;

import message.MessageContent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /conversations/{conversationId}/messages. The contract only accepts TEXT and EMOTION.
 * replyTo is optional and not in the contract yet.
 */
public final class SendMessageRequest {
    private final String type;
    private final String body;
    private final String clientMsgId;
    private final String replyTo;

    @JsonCreator
    public SendMessageRequest(
        @JsonProperty("type") String type,
        @JsonProperty("body") String body,
        @JsonProperty("clientMsgId") String clientMsgId,
        @JsonProperty("replyTo") String replyTo) {
        this.type = type;
        this.body = body;
        this.clientMsgId = clientMsgId;
        this.replyTo = replyTo;
    }

    /**
     * Returns null when the type is not one the contract accepts yet.
     */
    public MessageContent toContent() {
        if ("TEXT".equals(type)) {
            return new MessageContent.Text(body);
        }
        if ("EMOTION".equals(type)) {
            return new MessageContent.Emotion(body);
        }
        return null;
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

    public String getReplyTo() {
        return replyTo;
    }
}
