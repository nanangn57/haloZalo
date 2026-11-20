package analytics.query;

public final class DayTotal {
    private final String day;
    private final long total;

    public DayTotal(String day, long total) {
        this.day = day;
        this.total = total;
    }

    public String getDay() {
        return day;
    }

    public long getTotal() {
        return total;
    }
}
