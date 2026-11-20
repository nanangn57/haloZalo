package message;

import java.nio.charset.StandardCharsets;

/**
 * Checks the shape of content. File type and size limits are checked once, at upload, by media.MediaService.
 */
public final class ContentValidator {
    public static final int TEXT_MAX_BYTES = 4096;

    private ContentValidator() {
    }

    /**
     * Returns the reason the content is invalid, or null when it is valid.
     */
    public static String problem(MessageContent content) {
        if (content == null) {
            return "Message content is required";
        }
        if (content instanceof MessageContent.Text text) {
            if (text.text() == null || text.text().isEmpty()) {
                return "Text body is required";
            }
            return tooLong(text.text()) ? "Text body exceeds " + TEXT_MAX_BYTES + " bytes" : null;
        }
        if (content instanceof MessageContent.Emotion emotion) {
            return MessageContent.EMOTION_CODES.contains(emotion.code()) ? null : "Unknown emotion code";
        }
        MessageContent.Attachment attachment = (MessageContent.Attachment) content;
        if (blank(attachment.mediaId()) || blank(attachment.fileName()) || blank(attachment.mimeType())) {
            return "mediaId, fileName and mimeType are required";
        }
        if (attachment.size() <= 0) {
            return "File size must be positive";
        }
        if (attachment.caption() != null && tooLong(attachment.caption())) {
            return "Caption exceeds " + TEXT_MAX_BYTES + " bytes";
        }
        if (attachment instanceof MessageContent.Image image && (negative(image.width()) || negative(image.height()))) {
            return "Image size must be positive";
        }
        return null;
    }

    private static boolean tooLong(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX_BYTES;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean negative(Integer value) {
        return value != null && value <= 0;
    }
}
