package storage;

import conversation.ReadState;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryReadMarkers implements ReadMarkerRepository {
    private final Map<String, Long> markers = new ConcurrentHashMap<>();
    private final MemoryMessages messages;

    /**
     * Reads the messages to count unread ones, as the Postgres query does.
     */
    public MemoryReadMarkers(MemoryMessages messages) {
        this.messages = messages;
    }

    @Override
    public synchronized Advance advance(String conversationId, String userId, long seq, Instant at) {
        String key = conversationId + "|" + userId;
        long current = markers.getOrDefault(key, 0L);
        if (seq > current) {
            markers.put(key, seq);
            return new Advance(seq, true);
        }
        return new Advance(current, false);
    }

    @Override
    public Map<String, ReadState> readStates(String userId, Collection<String> conversationIds) {
        Map<String, ReadState> states = new HashMap<>();
        for (String id : conversationIds) {
            long lastRead = markers.getOrDefault(id + "|" + userId, 0L);
            long unread = messages.findAfterSeq(id, lastRead, Integer.MAX_VALUE).stream()
                .filter(message -> !message.getSenderId().equals(userId) && !message.isDeleted())
                .count();
            states.put(id, new ReadState(lastRead, unread));
        }
        return states;
    }
}
