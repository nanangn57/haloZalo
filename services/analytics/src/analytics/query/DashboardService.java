package analytics.query;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public final class DashboardService {
    private final DashboardQuery query;

    public DashboardService(DashboardQuery query) {
        this.query = query;
    }

    public Dashboard current() {
        return current(DateRange.ALL);
    }

    public Dashboard current(DateRange range) {
        LocalDate anchor = range.to() != null ? range.to() : LocalDate.now(ZoneOffset.UTC);
        List<DayTotal> messages = query.messagesByDay(range);
        long messageTotal = query.sumDayTotal(messages);
        long activeInRange = query.activeInRange(range);
        long messengers = query.messengersInRange(range);
        long friendRequests = query.friendRequests(range);
        long friendAccepted = query.friendAccepted(range);
        List<DayTotal> registrations = query.registrationsByDay(range);
        long registered = query.sumDayTotal(registrations);
        return new Dashboard(
            messages,
            query.activeUsers(),
            query.topGroups(10),
            query.distinctActive(anchor, anchor),
            query.distinctActive(anchor.minusDays(6), anchor),
            query.distinctActive(anchor.minusDays(29), anchor),
            registrations,
            new VerifyFunnel(
                registered,
                query.verifiedInRegistrationCohort(range),
                query.firstMessageInRegistrationCohort(range)
            ),
            activeInRange == 0 ? 0 : (double) messageTotal / activeInRange,
            activeInRange == 0 ? 0 : (double) messengers / activeInRange,
            friendRequests,
            friendRequests == 0 ? 0 : (double) friendAccepted / friendRequests,
            query.groupsCreated(range),
            query.avgMembersPerGroup(range),
            query.mediaByDay(range),
            query.platformHeatmap(),
            range
        );
    }

    public UserActivity mine(String userId) {
        return mine(userId, DateRange.ALL);
    }

    public UserActivity mine(String userId, DateRange range) {
        if (userId == null || userId.isBlank()) {
            throw new MissingSession();
        }
        return query.activityOf(userId, range);
    }

    public static final class MissingSession extends RuntimeException {
        public MissingSession() {
            super("Missing or invalid access token");
        }
    }
}
