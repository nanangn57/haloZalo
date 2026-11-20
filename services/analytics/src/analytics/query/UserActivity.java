package analytics.query;

public final class UserActivity {
    private final long logins;
    private final long messagesSent;
    private final long groupsCreated;

    public UserActivity(long logins, long messagesSent, long groupsCreated) {
        this.logins = logins;
        this.messagesSent = messagesSent;
        this.groupsCreated = groupsCreated;
    }

    public long getLogins() {
        return logins;
    }

    public long getMessagesSent() {
        return messagesSent;
    }

    public long getGroupsCreated() {
        return groupsCreated;
    }
}
