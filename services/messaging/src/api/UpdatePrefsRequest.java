package api;

import conversation.ConversationPrefsPatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of PATCH /conversations/{conversationId}/prefs. A field left out stays as it is.
 */
public final class UpdatePrefsRequest {
    private final Boolean pinned;
    private final Boolean muted;
    private final Boolean archived;

    @JsonCreator
    public UpdatePrefsRequest(
        @JsonProperty("pinned") Boolean pinned,
        @JsonProperty("muted") Boolean muted,
        @JsonProperty("archived") Boolean archived) {
        this.pinned = pinned;
        this.muted = muted;
        this.archived = archived;
    }

    public ConversationPrefsPatch toPatch() {
        return new ConversationPrefsPatch(pinned, muted, archived);
    }
}
