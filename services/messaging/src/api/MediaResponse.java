package api;

import media.Media;
import media.MediaLinks;
import message.MessageContent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The contract's Media: returned by the upload, and as message.media for IMAGE, VIDEO and DOCUMENT messages.
 * url is signed and expires; resolve it against the messaging base URL (new URL(url, base)). A fresh one comes with
 * every read of the message. width and height are left out when unknown.
 */
public final class MediaResponse {
    private final String mediaId;
    private final String kind;
    private final String url;
    private final String fileName;
    private final String mimeType;
    private final long size;
    private final Integer width;
    private final Integer height;

    public MediaResponse(Media media, MediaLinks links) {
        this(media.mediaId(), media.kind().name(), links, media.fileName(), media.mimeType(), media.size(),
            media.width(), media.height());
    }

    public MediaResponse(MessageContent.Attachment attachment, MediaLinks links) {
        this(attachment.mediaId(), attachment.type().name(), links, attachment.fileName(), attachment.mimeType(),
            attachment.size(),
            attachment instanceof MessageContent.Image image ? image.width() : null,
            attachment instanceof MessageContent.Image image ? image.height() : null);
    }

    private MediaResponse(
        String mediaId, String kind, MediaLinks links, String fileName, String mimeType, long size, Integer width,
        Integer height) {
        this.mediaId = mediaId;
        this.kind = kind;
        this.url = links.url(mediaId);
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.size = size;
        this.width = width;
        this.height = height;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getKind() {
        return kind;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getWidth() {
        return width;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getHeight() {
        return height;
    }
}
