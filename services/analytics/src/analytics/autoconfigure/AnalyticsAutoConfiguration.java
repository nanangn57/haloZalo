package analytics.autoconfigure;

import javax.sql.DataSource;

import analytics.record.ActivityRecorder;
import analytics.record.AggregateActivityRecorder;
import analytics.store.ActivityRepository;
import analytics.store.AnalyticsSchema;
import analytics.store.PostgresActivityRepository;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the recorder after the {@link DataSource} bean exists.
 * Component scan runs before DataSource auto-configuration, so
 * {@code @ConditionalOnBean(DataSource.class)} on a {@code @Component} never matches
 * and identity fails to start.
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
public class AnalyticsAutoConfiguration {
    @Bean
    ActivityRepository activityRepository(DataSource dataSource) {
        return new PostgresActivityRepository(dataSource);
    }

    @Bean
    ActivityRecorder activityRecorder(ActivityRepository activities) {
        return new AggregateActivityRecorder(activities);
    }

    @Bean
    AnalyticsSchema analyticsSchema(DataSource dataSource) {
        return new AnalyticsSchema(dataSource);
    }
}
