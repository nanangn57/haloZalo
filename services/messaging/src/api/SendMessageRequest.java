package api;

import message.MessageContent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /conversations/{conversationId}/messages. The contract only accepts TEXT and EMOTION.
 */
public final class SendMessageRequest {
    private final String type;
    private final String body;
    private final String clientMsgId;

    @JsonCreator
    public SendMessageRequest(
        @JsonProperty("type") String type,
        @JsonProperty("body") String body,
        @JsonProperty("clientMsgId") String clientMsgId) {
        this.type = type;
        this.body = body;
        this.clientMsgId = clientMsgId;
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
}
