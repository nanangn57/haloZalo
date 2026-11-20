package message;

import java.util.Set;

/**
 * Payload of a message. {@link Message#getType()} is the discriminator: each type has exactly one record here.
 */
public sealed interface MessageContent {
    Set<String> EMOTION_CODES = Set.of("like", "love", "haha", "wow", "sad", "angry");

    MessageType type();

    record Text(String text) implements MessageContent {
        @Override
        public MessageType type() {
            return MessageType.TEXT;
        }
    }

    record Emotion(String code) implements MessageContent {
        @Override
        public MessageType type() {
            return MessageType.EMOTION;
        }
    }

    /**
     * A file uploaded through POST /conversations/{conversationId}/media. Only the mediaId is stored: links to the
     * bytes are signed fresh each time the message is read, so a stored message never holds an expired link.
     * fileName, mimeType and size are copied from the upload so a message reads without a second lookup.
     */
    sealed interface Attachment extends MessageContent {
        String mediaId();

        String fileName();

        String mimeType();

        long size();

        /**
         * Optional text shown with the file. Returned as the message body.
         */
        String caption();
    }

    record Image(String mediaId, String fileName, String mimeType, long size, Integer width, Integer height, String caption)
        implements Attachment {
        @Override
        public MessageType type() {
            return MessageType.IMAGE;
        }
    }

    record Video(String mediaId, String fileName, String mimeType, long size, String caption) implements Attachment {
        @Override
        public MessageType type() {
            return MessageType.VIDEO;
        }
    }

    record Document(String mediaId, String fileName, String mimeType, long size, String caption) implements Attachment {
        @Override
        public MessageType type() {
            return MessageType.DOCUMENT;
        }
    }
}
