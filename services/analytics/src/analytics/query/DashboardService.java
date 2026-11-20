package analytics.query;

import org.springframework.stereotype.Service;

@Service
public final class DashboardService {
    private final DashboardQuery query;

    public DashboardService(DashboardQuery query) {
        this.query = query;
    }

    public Dashboard current() {
        return new Dashboard(query.messagesByDay(), query.activeUsers(), query.topGroups(10));
    }

    public UserActivity mine(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new MissingSession();
        }
        return query.activityOf(userId);
    }

    public static final class MissingSession extends RuntimeException {
        public MissingSession() {
            super("Missing or invalid access token");
        }
    }
}
