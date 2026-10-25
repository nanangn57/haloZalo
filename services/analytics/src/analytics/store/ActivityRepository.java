package analytics.store;

import java.time.Instant;

public interface ActivityRepository {
    /**
     * Stores the event once. A repeated eventId does not increase the total.
     *
     * @return true when this eventId was stored for the first time
     */
    boolean recordIfNew(String eventId, String type, Instant occurredAt);

    long count(String type);
}
