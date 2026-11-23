package analytics.record;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class Envelope {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String eventId;
    private final String type;
    private final Instant occurredAt;
    private final JsonNode payload;

    private Envelope(String eventId, String type, Instant occurredAt, JsonNode payload) {
        this.eventId = eventId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.payload = payload;
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
            return new Envelope(eventId, type, Instant.parse(occurredAt), node.get("payload"));
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

    String field(String name) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return text(payload, name);
    }

    Integer intField(String name) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode value = payload.get(name);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.intValue();
    }

    List<String> stringList(String name) {
        if (payload == null || !payload.isObject()) {
            return List.of();
        }
        JsonNode value = payload.get(name);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values;
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
