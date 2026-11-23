package identity.storage;

import identity.session.Session;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public final class PostgresSessionRepository implements SessionRepository {
    private static final String SELECT_COLS =
        "session_id, user_id, created_at, expires_at, device_id, device_label, user_agent, last_seen_at";

    private final DataSource dataSource;

    public PostgresSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Session session) {
        String sql = "INSERT INTO identity.sessions "
            + "(session_id, user_id, created_at, expires_at, device_id, device_label, user_agent, last_seen_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSession(statement, session);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save session", ex);
        }
    }

    @Override
    public Session findById(String sessionId) {
        String sql = "SELECT " + SELECT_COLS + " FROM identity.sessions WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return read(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read session", ex);
        }
    }

    @Override
    public Session findByUserIdAndDeviceId(String userId, String deviceId) {
        String sql = "SELECT " + SELECT_COLS
            + " FROM identity.sessions WHERE user_id = ? AND device_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return read(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read session by device", ex);
        }
    }

    @Override
    public List<Session> findByUserId(String userId) {
        String sql = "SELECT " + SELECT_COLS + " FROM identity.sessions WHERE user_id = ? ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Session> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(read(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not list sessions", ex);
        }
    }

    @Override
    public void delete(String sessionId) {
        String sql = "DELETE FROM identity.sessions WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete session", ex);
        }
    }

    @Override
    public void touch(String sessionId, Instant lastSeenAt, Instant expiresAt) {
        String sql = "UPDATE identity.sessions SET last_seen_at = ?, expires_at = ? WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, OffsetDateTime.ofInstant(lastSeenAt, ZoneOffset.UTC));
            statement.setObject(2, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            statement.setString(3, sessionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not touch session", ex);
        }
    }

    private static void bindSession(PreparedStatement statement, Session session) throws SQLException {
        statement.setString(1, session.getSessionId());
        statement.setString(2, session.getUserId());
        statement.setObject(3, OffsetDateTime.ofInstant(session.getCreatedAt(), ZoneOffset.UTC));
        statement.setObject(4, OffsetDateTime.ofInstant(session.getExpiresAt(), ZoneOffset.UTC));
        statement.setString(5, session.getDeviceId());
        statement.setString(6, session.getDeviceLabel());
        statement.setString(7, session.getUserAgent());
        if (session.getLastSeenAt() == null) {
            statement.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(8, OffsetDateTime.ofInstant(session.getLastSeenAt(), ZoneOffset.UTC));
        }
    }

    private static Session read(ResultSet rows) throws SQLException {
        OffsetDateTime lastSeen = rows.getObject("last_seen_at", OffsetDateTime.class);
        Instant lastSeenAt = lastSeen == null ? null : lastSeen.toInstant();
        return new Session(
            rows.getString("session_id"),
            rows.getString("user_id"),
            rows.getObject("created_at", OffsetDateTime.class).toInstant(),
            rows.getObject("expires_at", OffsetDateTime.class).toInstant(),
            rows.getString("device_id"),
            rows.getString("device_label"),
            rows.getString("user_agent"),
            lastSeenAt
        );
    }
}
