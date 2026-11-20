package app;

import javax.sql.DataSource;

import analytics.record.ActivityRecorder;
import analytics.record.AggregateActivityRecorder;
import analytics.query.DashboardQuery;
import analytics.store.ActivityBreakdown;
import analytics.store.ActivityRepository;
import analytics.store.AnalyticsSchema;
import analytics.store.PostgresActivityRepository;
import analytics.store.PostgresDashboardQuery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("app")
public class AppAnalyticsConfiguration {
    @Bean
    ActivityRepository activityRepository(DataSource dataSource) {
        return new PostgresActivityRepository(dataSource);
    }

    @Bean
    ActivityRecorder activityRecorder(ActivityRepository activities) {
        ActivityBreakdown breakdown = activities instanceof ActivityBreakdown noted
            ? noted
            : ActivityBreakdown.NONE;
        return new AggregateActivityRecorder(activities, breakdown);
    }

    @Bean
    DashboardQuery dashboardQuery(DataSource dataSource) {
        return new PostgresDashboardQuery(dataSource);
    }

    @Bean
    AnalyticsSchema analyticsSchema(DataSource dataSource) {
        return new AnalyticsSchema(dataSource);
    }
}
