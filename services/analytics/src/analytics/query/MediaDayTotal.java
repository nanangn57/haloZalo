package analytics.query;

public final class MediaDayTotal {
    private final String day;
    private final String type;
    private final long total;

    public MediaDayTotal(String day, String type, long total) {
        this.day = day;
        this.type = type;
        this.total = total;
    }

    public String getDay() {
        return day;
    }

    public String getType() {
        return type;
    }

    public long getTotal() {
        return total;
    }
}
