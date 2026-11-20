package analytics.store;

import java.time.Instant;

public interface ActivityBreakdown {
    ActivityBreakdown NONE = (type, occurredAt, actorId, subjectId) -> { };

    void note(String type, Instant occurredAt, String actorId, String subjectId);
}
