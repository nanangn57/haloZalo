package analytics.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresActivityRepositoryTest {
    @Test
    void totalsSqlStaysInsideTheAnalyticsSchema() {
        assertTrue(PostgresActivityRepository.INSERT_EVENT.startsWith("INSERT INTO analytics.recorded_events"));
        assertTrue(PostgresActivityRepository.INCREMENT.startsWith("INSERT INTO analytics.totals"));
        assertTrue(PostgresActivityRepository.COUNT.startsWith("SELECT total FROM analytics.totals"));
        assertFalse(PostgresActivityRepository.INSERT_EVENT.contains("messaging"));
        assertTrue(PostgresActivityRepository.INSERT_DAY.startsWith("INSERT INTO analytics.messages_by_day"));
        assertTrue(PostgresActivityRepository.INSERT_ACTIVE.startsWith("INSERT INTO analytics.active_users"));
        assertTrue(PostgresActivityRepository.INSERT_ACTIVE_DAY.startsWith("INSERT INTO analytics.active_users_by_day"));
        assertTrue(PostgresActivityRepository.INSERT_GROUP.startsWith("INSERT INTO analytics.known_groups"));
        assertTrue(PostgresActivityRepository.INSERT_USER.startsWith("INSERT INTO analytics.user_activity"));
        assertTrue(PostgresActivityRepository.INSERT_USER_DAY.startsWith("INSERT INTO analytics.user_activity_by_day"));
        assertTrue(PostgresActivityRepository.INSERT_HEATMAP.startsWith("INSERT INTO analytics.user_hour_weekday"));
        assertTrue(PostgresActivityRepository.INSERT_CONVERSATION.startsWith("INSERT INTO analytics.user_conversation_totals"));
        assertTrue(PostgresDashboardQuery.SELECT_DAYS.startsWith("SELECT day, total FROM analytics.messages_by_day"));
        assertTrue(PostgresDashboardQuery.SELECT_USER_DAY_SUM.contains("analytics.user_activity_by_day"));
        assertTrue(PostgresDashboardQuery.SELECT_DAYS_IN_RANGE.startsWith("SELECT day, total FROM analytics.messages_by_day"));
        assertFalse(PostgresDashboardQuery.SELECT_USER_DAY_SUM.contains("messaging"));
    }
}
