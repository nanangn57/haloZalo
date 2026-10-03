package message;

import java.util.Map;

/**
 * Optional auxiliary data on a message. Core fields such as sender, type, reply or status never go here.
 */
public final class MessageMetadata {
    private final Map<String, String> values;

    public MessageMetadata(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public Map<String, String> getValues() {
        return values;
    }
}
