package api;

import message.MessageContent;
import message.MessageType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /conversations/{conversationId}/messages. TEXT and EMOTION put the content in body. IMAGE, VIDEO and
 * DOCUMENT name an uploaded file with mediaId, and body is the optional caption. replyTo is optional.
 */
public final class SendMessageRequest {
    private final String type;
    private final String body;
    private final String mediaId;
    private final String clientMsgId;
    private final String replyTo;

    @JsonCreator
    public SendMessageRequest(
        @JsonProperty("type") String type,
        @JsonProperty("body") String body,
        @JsonProperty("mediaId") String mediaId,
        @JsonProperty("clientMsgId") String clientMsgId,
        @JsonProperty("replyTo") String replyTo) {
        this.type = type;
        this.body = body;
        this.mediaId = mediaId;
        this.clientMsgId = clientMsgId;
        this.replyTo = replyTo;
    }

    /**
     * The content of a TEXT or EMOTION message; null for any other type.
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

    /**
     * IMAGE, VIDEO or DOCUMENT; null for any other type. The content then comes from the uploaded media.
     */
    public MessageType mediaType() {
        return switch (type == null ? "" : type) {
            case "IMAGE" -> MessageType.IMAGE;
            case "VIDEO" -> MessageType.VIDEO;
            case "DOCUMENT" -> MessageType.DOCUMENT;
            default -> null;
        };
    }

    public String getType() {
        return type;
    }

    public String getBody() {
        return body;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public String getReplyTo() {
        return replyTo;
    }
}
