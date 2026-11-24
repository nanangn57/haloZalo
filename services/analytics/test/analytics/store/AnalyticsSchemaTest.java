package analytics.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsSchemaTest {
    @Test
    void stripLineCommentsRemovesSemicolonsInsideComments() {
        String sql = ""
            + "-- Layer C (admin dashboard; some are SUM-derivable)\n"
            + "CREATE SCHEMA IF NOT EXISTS analytics;\n"
            + "-- Prefer SUM; keep a C table\n"
            + "CREATE TABLE IF NOT EXISTS analytics.totals (type VARCHAR(80) NOT NULL);\n";

        String cleaned = AnalyticsSchema.stripLineComments(sql);
        assertFalse(cleaned.contains("some are"));
        assertFalse(cleaned.contains("Prefer SUM"));
        assertTrue(cleaned.contains("CREATE SCHEMA IF NOT EXISTS analytics"));
        assertTrue(cleaned.contains("CREATE TABLE IF NOT EXISTS analytics.totals"));

        for (String command : cleaned.split(";")) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty()) {
                assertTrue(trimmed.startsWith("CREATE"), trimmed);
            }
        }
    }
}
