package storage;

import message.MessageContent;
import message.MessageType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * JSONB form of message content and metadata. The type column says which record the content is.
 */
final class MessageContentJson {
    private static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        // Rows written by a newer version may carry fields this version does not know yet.
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private MessageContentJson() {
    }

    static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write JSON", ex);
        }
    }

    static MessageContent content(MessageType type, String json) {
        Class<? extends MessageContent> shape = switch (type) {
            case TEXT -> MessageContent.Text.class;
            case EMOTION -> MessageContent.Emotion.class;
            case IMAGE -> MessageContent.Image.class;
            case VIDEO -> MessageContent.Video.class;
            case DOCUMENT -> MessageContent.Document.class;
        };
        try {
            return JSON.readValue(json, shape);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read " + type + " content", ex);
        }
    }

    static Map<String, String> metadata(String json) {
        try {
            return JSON.readValue(json, STRING_MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read metadata", ex);
        }
    }
}
