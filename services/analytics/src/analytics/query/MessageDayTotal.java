package analytics.query;

public final class MessageDayTotal {
    private final String day;
    private final long sent;
    private final long received;

    public MessageDayTotal(String day, long sent, long received) {
        this.day = day;
        this.sent = sent;
        this.received = received;
    }

    public String getDay() {
        return day;
    }

    public long getSent() {
        return sent;
    }

    public long getReceived() {
        return received;
    }
}
