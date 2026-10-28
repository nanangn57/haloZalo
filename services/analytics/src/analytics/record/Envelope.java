package analytics.record;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class Envelope {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String eventId;
    private final String type;
    private final Instant occurredAt;

    private Envelope(String eventId, String type, Instant occurredAt) {
        this.eventId = eventId;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    static Envelope parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(json);
            String eventId = text(node, "eventId");
            String type = text(node, "type");
            String occurredAt = text(node, "occurredAt");
            if (eventId == null || type == null || occurredAt == null) {
                return null;
            }
            return new Envelope(eventId, type, Instant.parse(occurredAt));
        } catch (JsonProcessingException | RuntimeException ex) {
            return null;
        }
    }

    String eventId() {
        return eventId;
    }

    String type() {
        return type;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
