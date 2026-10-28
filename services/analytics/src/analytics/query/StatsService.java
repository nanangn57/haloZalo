package analytics.query;

import analytics.record.ActivityTypes;
import analytics.store.ActivityRepository;

import org.springframework.stereotype.Service;

@Service
public final class StatsService {
    private final ActivityRepository activities;

    public StatsService(ActivityRepository activities) {
        this.activities = activities;
    }

    public ActivityStats current() {
        return new ActivityStats(
            activities.count(ActivityTypes.LOGGED_IN),
            activities.count(ActivityTypes.GROUP_CREATED),
            activities.count(ActivityTypes.MESSAGE_CREATED)
        );
    }
}
