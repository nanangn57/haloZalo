package analytics.query;

public final class DirectGroupSplit {
    private final long direct;
    private final long group;

    public DirectGroupSplit(long direct, long group) {
        this.direct = direct;
        this.group = group;
    }

    public long getDirect() {
        return direct;
    }

    public long getGroup() {
        return group;
    }
}
