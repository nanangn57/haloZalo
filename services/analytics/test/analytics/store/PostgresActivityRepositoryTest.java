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
        assertFalse(PostgresActivityRepository.INCREMENT.contains("messaging"));
        assertFalse(PostgresActivityRepository.COUNT.contains("messaging"));
        assertTrue(PostgresActivityRepository.INSERT_DAY.startsWith("INSERT INTO analytics.messages_by_day"));
        assertTrue(PostgresActivityRepository.INSERT_ACTIVE.startsWith("INSERT INTO analytics.active_users"));
        assertTrue(PostgresActivityRepository.INSERT_GROUP.startsWith("INSERT INTO analytics.known_groups"));
        assertTrue(PostgresActivityRepository.GROUP_KNOWN.startsWith("SELECT 1 FROM analytics.known_groups"));
        assertTrue(PostgresActivityRepository.INSERT_GROUP_TOTAL.startsWith("INSERT INTO analytics.group_message_totals"));
        assertTrue(PostgresActivityRepository.INSERT_USER.startsWith("INSERT INTO analytics.user_activity"));
        assertFalse(PostgresActivityRepository.INSERT_DAY.contains("messaging"));
        assertTrue(PostgresDashboardQuery.SELECT_DAYS.startsWith("SELECT day, total FROM analytics.messages_by_day"));
        assertTrue(PostgresDashboardQuery.SELECT_ACTIVE.startsWith("SELECT COUNT(*) FROM analytics.active_users"));
        assertTrue(PostgresDashboardQuery.SELECT_TOP.startsWith("SELECT group_id, total FROM analytics.group_message_totals"));
        assertTrue(PostgresDashboardQuery.SELECT_USER.startsWith("SELECT logins, messages_sent, groups_created FROM analytics.user_activity"));
        assertFalse(PostgresDashboardQuery.SELECT_TOP.contains("messaging"));
        assertFalse(PostgresDashboardQuery.SELECT_USER.contains("messaging"));
    }
}
