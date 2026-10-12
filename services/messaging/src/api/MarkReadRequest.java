package api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of PUT /conversations/{conversationId}/read: the highest seq the user has now seen.
 */
public final class MarkReadRequest {
    private final Long seq;

    @JsonCreator
    public MarkReadRequest(@JsonProperty("seq") Long seq) {
        this.seq = seq;
    }

    public Long getSeq() {
        return seq;
    }
}
