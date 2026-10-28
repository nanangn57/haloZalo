package api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /conversations/{conversationId}/forwards. messageId is the message being forwarded.
 */
public final class ForwardMessageRequest {
    private final String messageId;
    private final String clientMsgId;

    @JsonCreator
    public ForwardMessageRequest(
        @JsonProperty("messageId") String messageId,
        @JsonProperty("clientMsgId") String clientMsgId) {
        this.messageId = messageId;
        this.clientMsgId = clientMsgId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }
}
