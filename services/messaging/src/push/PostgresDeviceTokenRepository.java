package push;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

@Component
public final class PostgresDeviceTokenRepository implements DeviceTokenRepository {
    private final DataSource dataSource;

    public PostgresDeviceTokenRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void upsert(String userId, String token, String platform) {
        String sql = """
            INSERT INTO messaging.device_tokens (user_id, token, platform, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, token) DO UPDATE
            SET platform = EXCLUDED.platform, updated_at = EXCLUDED.updated_at
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindUuid(statement, 1, userId);
            statement.setString(2, token);
            statement.setString(3, platform == null || platform.isBlank() ? "unknown" : platform);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save device token", ex);
        }
    }

    @Override
    public void delete(String userId, String token) {
        String sql = "DELETE FROM messaging.device_tokens WHERE user_id = ? AND token = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindUuid(statement, 1, userId);
            statement.setString(2, token);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete device token", ex);
        }
    }

    @Override
    public List<String> tokensOf(String userId) {
        String sql = "SELECT token FROM messaging.device_tokens WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindUuid(statement, 1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> tokens = new ArrayList<>();
                while (rows.next()) {
                    tokens.add(rows.getString("token"));
                }
                return tokens;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read device tokens", ex);
        }
    }

    private static void bindUuid(PreparedStatement statement, int index, String userId) throws SQLException {
        statement.setObject(index, UUID.fromString(userId), Types.OTHER);
    }
}
