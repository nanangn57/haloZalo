package analytics.record;

import analytics.store.ActivityBreakdown;
import analytics.store.ActivityRepository;

public final class AggregateActivityRecorder implements ActivityRecorder {
    private final ActivityRepository activities;
    private final ActivityBreakdown breakdown;

    public AggregateActivityRecorder(ActivityRepository activities) {
        this(activities, ActivityBreakdown.NONE);
    }

    public AggregateActivityRecorder(ActivityRepository activities, ActivityBreakdown breakdown) {
        this.activities = activities;
        this.breakdown = breakdown;
    }

    @Override
    public void record(String envelope) {
        Envelope parsed = Envelope.parse(envelope);
        if (parsed == null || !ActivityTypes.isTracked(parsed.type())) {
            return;
        }
        if (activities.recordIfNew(parsed.eventId(), parsed.type(), parsed.occurredAt())) {
            breakdown.note(parsed.type(), parsed.occurredAt(), actor(parsed), subject(parsed));
        }
    }

    private static String actor(Envelope parsed) {
        if (ActivityTypes.LOGGED_IN.equals(parsed.type())) {
            return parsed.field("userId");
        }
        if (ActivityTypes.GROUP_CREATED.equals(parsed.type())) {
            return parsed.field("ownerId");
        }
        if (ActivityTypes.MESSAGE_CREATED.equals(parsed.type())) {
            return parsed.field("senderId");
        }
        return null;
    }

    private static String subject(Envelope parsed) {
        if (ActivityTypes.GROUP_CREATED.equals(parsed.type())) {
            return parsed.field("groupId");
        }
        if (ActivityTypes.MESSAGE_CREATED.equals(parsed.type())) {
            return parsed.field("conversationId");
        }
        return null;
    }
}
