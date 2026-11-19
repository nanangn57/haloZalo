package analytics.record;

import analytics.store.ActivityRepository;

public final class AggregateActivityRecorder implements ActivityRecorder {
    private final ActivityRepository activities;

    public AggregateActivityRecorder(ActivityRepository activities) {
        this.activities = activities;
    }

    @Override
    public void record(String envelope) {
        Envelope parsed = Envelope.parse(envelope);
        if (parsed == null || !ActivityTypes.isTracked(parsed.type())) {
            return;
        }
        activities.recordIfNew(parsed.eventId(), parsed.type(), parsed.occurredAt());
    }
}
