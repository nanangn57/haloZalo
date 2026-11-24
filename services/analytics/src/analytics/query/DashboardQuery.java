package analytics.query;

import java.util.List;

public interface DashboardQuery {
    List<DayTotal> messagesByDay();

    default List<DayTotal> messagesByDay(DateRange range) {
        return messagesByDay().stream()
            .filter(day -> range.contains(java.time.LocalDate.parse(day.getDay())))
            .toList();
    }

    long activeUsers();

    List<GroupTotal> topGroups(int limit);

    UserActivity activityOf(String userId, DateRange range);

    long distinctActive(java.time.LocalDate from, java.time.LocalDate to);

    List<DayTotal> registrationsByDay(DateRange range);

    long sumDayTotal(List<DayTotal> days);

    long verifications(DateRange range);

    /** Users registered in range who also have a verification row (cohort). */
    long verifiedInRegistrationCohort(DateRange range);

    /** Users registered in range who have sent ≥1 message lifetime (cohort). */
    long firstMessageInRegistrationCohort(DateRange range);

    long messengersInRange(DateRange range);

    long activeInRange(DateRange range);

    long friendRequests(DateRange range);

    long friendAccepted(DateRange range);

    long groupsCreated(DateRange range);

    double avgMembersPerGroup(DateRange range);

    List<MediaDayTotal> mediaByDay(DateRange range);

    List<HeatmapCell> platformHeatmap();
}
