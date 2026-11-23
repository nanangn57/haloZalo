package analytics.query;

public final class HeatmapCell {
    private final int weekday;
    private final int hour;
    private final long total;

    public HeatmapCell(int weekday, int hour, long total) {
        this.weekday = weekday;
        this.hour = hour;
        this.total = total;
    }

    public int getWeekday() {
        return weekday;
    }

    public int getHour() {
        return hour;
    }

    public long getTotal() {
        return total;
    }
}
