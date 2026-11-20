package analytics.store;

import analytics.record.ActivityTypes;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

public final class PostgresActivityRepository implements ActivityRepository, ActivityBreakdown {
    static final String INSERT_EVENT =
        "INSERT INTO analytics.recorded_events (event_id, type, occurred_at) VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING";
    static final String INCREMENT =
        "INSERT INTO analytics.totals (type, total) VALUES (?, 1) ON CONFLICT (type) DO UPDATE SET total = analytics.totals.total + 1";
    static final String COUNT = "SELECT total FROM analytics.totals WHERE type = ?";
    static final String INSERT_DAY =
        "INSERT INTO analytics.messages_by_day (day, total) VALUES (?, 1) ON CONFLICT (day) DO UPDATE SET total = analytics.messages_by_day.total + 1";
    static final String INSERT_ACTIVE =
        "INSERT INTO analytics.active_users (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING";
    static final String INSERT_GROUP =
        "INSERT INTO analytics.known_groups (group_id) VALUES (?) ON CONFLICT (group_id) DO NOTHING";
    static final String GROUP_KNOWN = "SELECT 1 FROM analytics.known_groups WHERE group_id = ?";
    static final String INSERT_GROUP_TOTAL =
        "INSERT INTO analytics.group_message_totals (group_id, total) VALUES (?, 1) ON CONFLICT (group_id) DO UPDATE SET total = analytics.group_message_totals.total + 1";
    static final String INSERT_USER =
        "INSERT INTO analytics.user_activity (user_id, logins, messages_sent, groups_created) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT (user_id) DO UPDATE SET "
            + "logins = analytics.user_activity.logins + EXCLUDED.logins, "
            + "messages_sent = analytics.user_activity.messages_sent + EXCLUDED.messages_sent, "
            + "groups_created = analytics.user_activity.groups_created + EXCLUDED.groups_created";

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

    @Override
    public void note(String type, Instant occurredAt, String actorId, String subjectId) {
        try (Connection connection = dataSource.getConnection()) {
            if (ActivityTypes.LOGGED_IN.equals(type)) {
                markActive(connection, actorId);
                addUser(connection, actorId, 1, 0, 0);
            } else if (ActivityTypes.GROUP_CREATED.equals(type)) {
                rememberGroup(connection, subjectId);
                addUser(connection, actorId, 0, 0, 1);
            } else if (ActivityTypes.MESSAGE_CREATED.equals(type)) {
                addDay(connection, occurredAt);
                markActive(connection, actorId);
                addUser(connection, actorId, 0, 1, 0);
                if (knownGroup(connection, subjectId)) {
                    addGroupTotal(connection, subjectId);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not record activity breakdown", ex);
        }
    }

    private static void addDay(Connection connection, Instant occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DAY)) {
            LocalDate day = occurredAt.atOffset(ZoneOffset.UTC).toLocalDate();
            statement.setDate(1, Date.valueOf(day));
            statement.executeUpdate();
        }
    }

    private static void markActive(Connection connection, String userId) throws SQLException {
        if (userId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ACTIVE)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
    }

    private static void rememberGroup(Connection connection, String groupId) throws SQLException {
        if (groupId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_GROUP)) {
            statement.setString(1, groupId);
            statement.executeUpdate();
        }
    }

    private static boolean knownGroup(Connection connection, String groupId) throws SQLException {
        if (groupId == null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(GROUP_KNOWN)) {
            statement.setString(1, groupId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void addGroupTotal(Connection connection, String groupId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_GROUP_TOTAL)) {
            statement.setString(1, groupId);
            statement.executeUpdate();
        }
    }

    private static void addUser(Connection connection, String userId, long logins, long messages, long groups) throws SQLException {
        if (userId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, userId);
            statement.setLong(2, logins);
            statement.setLong(3, messages);
            statement.setLong(4, groups);
            statement.executeUpdate();
        }
    }
}
