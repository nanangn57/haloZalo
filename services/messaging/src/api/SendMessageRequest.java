package api;

import message.MessageContent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /conversations/{conversationId}/messages.
 */
public final class SendMessageRequest {
    private final String type;
    private final String body;
    private final String clientMsgId;
    private final String replyTo;
    private final String thumbnailUrl;
    private final Integer width;
    private final Integer height;
    private final Integer duration;
    private final String caption;
    private final String fileName;
    private final String mimeType;
    private final Long size;

    @JsonCreator
    public SendMessageRequest(
        @JsonProperty("type") String type,
        @JsonProperty("body") String body,
        @JsonProperty("clientMsgId") String clientMsgId,
        @JsonProperty("replyTo") String replyTo,
        @JsonProperty("thumbnailUrl") String thumbnailUrl,
        @JsonProperty("width") Integer width,
        @JsonProperty("height") Integer height,
        @JsonProperty("duration") Integer duration,
        @JsonProperty("caption") String caption,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("mimeType") String mimeType,
        @JsonProperty("size") Long size
    ) {
        this.type = type;
        this.body = body;
        this.clientMsgId = clientMsgId;
        this.replyTo = replyTo;
        this.thumbnailUrl = thumbnailUrl;
        this.width = width;
        this.height = height;
        this.duration = duration;
        this.caption = caption;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.size = size;
    }

    /**
     * Returns null when the type is not accepted.
     */
    public MessageContent toContent() {
        if ("TEXT".equals(type)) {
            return new MessageContent.Text(body);
        }
        if ("EMOTION".equals(type)) {
            return new MessageContent.Emotion(body);
        }
        if ("IMAGE".equals(type)) {
            return new MessageContent.Image(body, thumbnailUrl, width, height, caption);
        }
        if ("VIDEO".equals(type)) {
            return new MessageContent.Video(body, thumbnailUrl, width, height, duration, caption);
        }
        if ("DOCUMENT".equals(type)) {
            long documentSize = size == null ? 0L : size;
            return new MessageContent.Document(body, fileName, mimeType, documentSize);
        }
        if ("AUDIO".equals(type)) {
            long audioSize = size == null ? 0L : size;
            return new MessageContent.Audio(body, mimeType, audioSize, duration, fileName);
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
