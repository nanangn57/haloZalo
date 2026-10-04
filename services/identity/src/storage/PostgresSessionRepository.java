package storage;

import session.Session;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

@Component
public final class PostgresSessionRepository implements SessionRepository {
    private final DataSource dataSource;

    public PostgresSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Session session) {
        String sql = "INSERT INTO identity.sessions (session_id, user_id, expires_at) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, session.getSessionId());
            statement.setString(2, session.getUserId());
            statement.setObject(3, OffsetDateTime.ofInstant(session.getExpiresAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save session", ex);
        }
    }

    @Override
    public Session findById(String sessionId) {
        String sql = "SELECT session_id, user_id, expires_at FROM identity.sessions WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new Session(
                    rows.getString("session_id"),
                    rows.getString("user_id"),
                    rows.getObject("expires_at", OffsetDateTime.class).toInstant()
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read session", ex);
        }
    }
}
