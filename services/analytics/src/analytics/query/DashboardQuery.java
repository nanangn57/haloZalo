package analytics.query;

import java.util.List;

public interface DashboardQuery {
    List<DayTotal> messagesByDay();

    long activeUsers();

    List<GroupTotal> topGroups(int limit);

    UserActivity activityOf(String userId);
}
