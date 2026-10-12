package storage;

import conversation.ReadState;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public interface ReadMarkerRepository {
    /**
     * Moves the user's marker in the conversation up to seq, and never back: an older device cannot undo a newer
     * read. Returns the marker after the call and whether it moved.
     */
    Advance advance(String conversationId, String userId, long seq, Instant at);

    /**
     * The user's read state in each given conversation, by conversation id, in one round trip.
     */
    Map<String, ReadState> readStates(String userId, Collection<String> conversationIds);

    record Advance(long lastReadSeq, boolean moved) {
    }
}
