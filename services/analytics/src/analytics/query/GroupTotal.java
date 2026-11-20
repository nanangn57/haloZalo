package analytics.query;

public final class GroupTotal {
    private final String groupId;
    private final long total;

    public GroupTotal(String groupId, long total) {
        this.groupId = groupId;
        this.total = total;
    }

    public String getGroupId() {
        return groupId;
    }

    public long getTotal() {
        return total;
    }
}
