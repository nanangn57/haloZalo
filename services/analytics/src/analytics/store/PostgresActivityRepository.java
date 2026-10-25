package analytics.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DataSource.class)
public final class PostgresActivityRepository implements ActivityRepository {
    static final String INSERT_EVENT =
        "INSERT INTO analytics.recorded_events (event_id, type, occurred_at) VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING";
    static final String INCREMENT =
        "INSERT INTO analytics.totals (type, total) VALUES (?, 1) ON CONFLICT (type) DO UPDATE SET total = analytics.totals.total + 1";
    static final String COUNT = "SELECT total FROM analytics.totals WHERE type = ?";

    private final DataSource dataSource;

    public PostgresActivityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean recordIfNew(String eventId, String type, Instant occurredAt) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT)) {
                    statement.setString(1, eventId);
                    statement.setString(2, type);
                    statement.setObject(3, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    try (PreparedStatement statement = connection.prepareStatement(INCREMENT)) {
                        statement.setString(1, type);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return inserted == 1;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not record activity", ex);
        }
    }

    @Override
    public long count(String type) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT)) {
            statement.setString(1, type);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return 0;
                }
                return rows.getLong("total");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read activity total", ex);
        }
    }
}
