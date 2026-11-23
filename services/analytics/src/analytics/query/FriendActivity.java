package analytics.query;

public final class FriendActivity {
    private final long count;
    private final long requestsSent;
    private final long requestsReceived;
    private final double acceptRate;

    public FriendActivity(long count, long requestsSent, long requestsReceived, double acceptRate) {
        this.count = count;
        this.requestsSent = requestsSent;
        this.requestsReceived = requestsReceived;
        this.acceptRate = acceptRate;
    }

    public long getCount() {
        return count;
    }

    public long getRequestsSent() {
        return requestsSent;
    }

    public long getRequestsReceived() {
        return requestsReceived;
    }

    public double getAcceptRate() {
        return acceptRate;
    }
}
