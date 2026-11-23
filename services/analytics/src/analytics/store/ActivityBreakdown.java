package analytics.store;

import java.time.Instant;

public interface ActivityBreakdown {
    ActivityBreakdown NONE = note -> { };

    void note(ActivityNote note);

    /** Compatibility for older call sites that only pass actor/subject. */
    default void note(String type, Instant occurredAt, String actorId, String subjectId) {
        note(ActivityNote.of(type, occurredAt, actorId, subjectId));
    }
}
