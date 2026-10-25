package analytics.query;

public final class ActivityStats {
    private final long logins;
    private final long groupsCreated;
    private final long messagesCreated;

    public ActivityStats(long logins, long groupsCreated, long messagesCreated) {
        this.logins = logins;
        this.groupsCreated = groupsCreated;
        this.messagesCreated = messagesCreated;
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
}
