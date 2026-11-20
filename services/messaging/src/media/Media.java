package media;

import message.MessageType;

import java.time.Instant;

/**
 * One uploaded file. It belongs to the conversation it was uploaded to, and only its uploader can send it there.
 * kind is IMAGE, VIDEO or DOCUMENT. width and height are set for images whose header could be read.
 */
public record Media(
    String mediaId,
    String conversationId,
    String uploaderId,
    MessageType kind,
    String fileName,
    String mimeType,
    long size,
    Integer width,
    Integer height,
    Instant createdAt) {
}
