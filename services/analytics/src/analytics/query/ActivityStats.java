package analytics.query;

public final class ActivityStats {
    private final long logins;
    private final long groupsCreated;
    private final long messagesCreated;
    private final long mediaUploads;

    public ActivityStats(long logins, long groupsCreated, long messagesCreated, long mediaUploads) {
        this.logins = logins;
        this.groupsCreated = groupsCreated;
        this.messagesCreated = messagesCreated;
        this.mediaUploads = mediaUploads;
    }

    public long getLogins() {
        return logins;
    }

    public long getGroupsCreated() {
        return groupsCreated;
    }

    public long getMessagesCreated() {
        return messagesCreated;
    }

    public long getMediaUploads() {
        return mediaUploads;
    }
}
