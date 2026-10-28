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

    record Image(String url, String thumbnailUrl, Integer width, Integer height, String caption) implements MessageContent {
        @Override
        public MessageType type() {
            return MessageType.IMAGE;
        }
    }

    record Video(String url, String thumbnailUrl, Integer width, Integer height, Integer duration, String caption)
        implements MessageContent {
        @Override
        public MessageType type() {
            return MessageType.VIDEO;
        }
    }

    record Document(String url, String fileName, String mimeType, long size) implements MessageContent {
        @Override
        public MessageType type() {
            return MessageType.DOCUMENT;
        }
    }
}
