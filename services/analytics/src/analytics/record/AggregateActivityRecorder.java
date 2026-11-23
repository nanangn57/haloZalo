package analytics.record;

import analytics.store.ActivityBreakdown;
import analytics.store.ActivityNote;
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
            breakdown.note(toNote(parsed));
        }
    }

    private static ActivityNote toNote(Envelope parsed) {
        return new ActivityNote(
            parsed.type(),
            parsed.occurredAt(),
            actor(parsed),
            subject(parsed),
            parsed.field("conversationType"),
            parsed.stringList("recipientIds"),
            contentType(parsed),
            parsed.intField("memberCount"),
            parsed.field("fromUserId"),
            parsed.field("toUserId")
        );
    }

    private static String actor(Envelope parsed) {
        if (ActivityTypes.REGISTERED.equals(parsed.type())
            || ActivityTypes.VERIFIED.equals(parsed.type())
            || ActivityTypes.LOGGED_IN.equals(parsed.type())) {
            return parsed.field("userId");
        }
        if (ActivityTypes.GROUP_CREATED.equals(parsed.type())) {
            return parsed.field("ownerId");
        }
        if (ActivityTypes.FRIEND_REQUEST_SENT.equals(parsed.type())
            || ActivityTypes.FRIEND_REQUEST_ACCEPTED.equals(parsed.type())) {
            return parsed.field("fromUserId");
        }
        if (ActivityTypes.MESSAGE_CREATED.equals(parsed.type())
            || ActivityTypes.MEDIA_UPLOADED.equals(parsed.type())) {
            return parsed.field("senderId");
        }
        return null;
    }

    private static String subject(Envelope parsed) {
        if (ActivityTypes.GROUP_CREATED.equals(parsed.type())) {
            return parsed.field("groupId");
        }
        if (ActivityTypes.MESSAGE_CREATED.equals(parsed.type())
            || ActivityTypes.MEDIA_UPLOADED.equals(parsed.type())) {
            return parsed.field("conversationId");
        }
        if (ActivityTypes.FRIEND_REQUEST_SENT.equals(parsed.type())
            || ActivityTypes.FRIEND_REQUEST_ACCEPTED.equals(parsed.type())) {
            return parsed.field("toUserId");
        }
        return null;
    }

    private static String contentType(Envelope parsed) {
        if (ActivityTypes.MESSAGE_CREATED.equals(parsed.type())
            || ActivityTypes.MEDIA_UPLOADED.equals(parsed.type())) {
            return parsed.field("type");
        }
        return null;
    }
}
