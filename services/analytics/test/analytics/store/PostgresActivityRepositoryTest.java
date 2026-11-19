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
    }
}
