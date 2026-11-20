package analytics.query;

import java.util.List;

public final class Dashboard {
    private final List<DayTotal> messagesByDay;
    private final long activeUsers;
    private final List<GroupTotal> topGroups;

    public Dashboard(List<DayTotal> messagesByDay, long activeUsers, List<GroupTotal> topGroups) {
        this.messagesByDay = messagesByDay;
        this.activeUsers = activeUsers;
        this.topGroups = topGroups;
    }

    public List<DayTotal> getMessagesByDay() {
        return messagesByDay;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public List<GroupTotal> getTopGroups() {
        return topGroups;
    }
}
