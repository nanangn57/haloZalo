package message;

import java.nio.charset.StandardCharsets;

public final class ContentValidator {
    public static final int TEXT_MAX_BYTES = 4096;
    public static final long DOCUMENT_MAX_BYTES = 10L * 1024 * 1024;

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
            if (text.text().getBytes(StandardCharsets.UTF_8).length > TEXT_MAX_BYTES) {
                return "Text body exceeds " + TEXT_MAX_BYTES + " bytes";
            }
            return null;
        }
        if (content instanceof MessageContent.Emotion emotion) {
            return MessageContent.EMOTION_CODES.contains(emotion.code()) ? null : "Unknown emotion code";
        }
        if (content instanceof MessageContent.Image image) {
            if (blank(image.url())) {
                return "Image url is required";
            }
            return negative(image.width()) || negative(image.height()) ? "Image size must be positive" : null;
        }
        if (content instanceof MessageContent.Video video) {
            if (blank(video.url())) {
                return "Video url is required";
            }
            if (negative(video.width()) || negative(video.height()) || negative(video.duration())) {
                return "Video size and duration must be positive";
            }
            return null;
        }
        MessageContent.Document document = (MessageContent.Document) content;
        if (blank(document.url()) || blank(document.fileName()) || blank(document.mimeType())) {
            return "Document url, fileName and mimeType are required";
        }
        if (document.size() <= 0 || document.size() > DOCUMENT_MAX_BYTES) {
            return "Document size must be between 1 and " + DOCUMENT_MAX_BYTES + " bytes";
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean negative(Integer value) {
        return value != null && value <= 0;
    }
}
