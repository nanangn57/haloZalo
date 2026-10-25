package analytics.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Creates schema {@code analytics} when this module shares a process that has a database.
 * Statements are idempotent, so identity's startup script can run them too.
 */
@Component
@ConditionalOnBean(DataSource.class)
public final class AnalyticsSchema {
    public AnalyticsSchema(DataSource dataSource) {
        String sql = script();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not create schema analytics", failure);
        }
    }

    private static String script() {
        try (InputStream in = AnalyticsSchema.class.getResourceAsStream("/analytics-schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("analytics-schema.sql is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read analytics-schema.sql", failure);
        }
    }
}
