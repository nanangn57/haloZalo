package analytics.store;

import java.time.Instant;
import java.util.List;

/**
 * One tracked event ready for breakdown tables. Null optional fields mean the event did not carry them.
 */
public final class ActivityNote {
    private final String type;
    private final Instant occurredAt;
    private final String actorId;
    private final String subjectId;
    private final String conversationType;
    private final List<String> recipientIds;
    private final String contentType;
    private final Integer memberCount;
    private final String fromUserId;
    private final String toUserId;

    public ActivityNote(
        String type,
        Instant occurredAt,
        String actorId,
        String subjectId,
        String conversationType,
        List<String> recipientIds,
        String contentType,
        Integer memberCount,
        String fromUserId,
        String toUserId
    ) {
        this.type = type;
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.subjectId = subjectId;
        this.conversationType = conversationType;
        this.recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
        this.contentType = contentType;
        this.memberCount = memberCount;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
    }

    public static ActivityNote of(String type, Instant occurredAt, String actorId, String subjectId) {
        return new ActivityNote(type, occurredAt, actorId, subjectId, null, List.of(), null, null, null, null);
    }

    public String type() {
        return type;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String actorId() {
        return actorId;
    }

    public String subjectId() {
        return subjectId;
    }

    public String conversationType() {
        return conversationType;
    }

    public List<String> recipientIds() {
        return recipientIds;
    }

    public String contentType() {
        return contentType;
    }

    public Integer memberCount() {
        return memberCount;
    }

    public String fromUserId() {
        return fromUserId;
    }

    public String toUserId() {
        return toUserId;
    }
}
