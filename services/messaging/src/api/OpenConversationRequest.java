package api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body of POST /internal/conversations. conversationId is optional: v1 clients generate it themselves.
 */
public final class OpenConversationRequest {
    private final String conversationId;
    private final String type;
    private final List<String> memberIds;

    @JsonCreator
    public OpenConversationRequest(
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("type") String type,
        @JsonProperty("memberIds") List<String> memberIds) {
        this.conversationId = conversationId;
        this.type = type;
        this.memberIds = memberIds;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getType() {
        return type;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }
}
