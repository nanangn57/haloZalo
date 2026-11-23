package identity.storage;

import identity.password.PasswordReset;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

@Component
public final class PostgresPasswordResetRepository implements PasswordResetRepository {
    private final DataSource dataSource;

    public PostgresPasswordResetRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(PasswordReset reset) {
        String sql = "INSERT INTO identity.password_resets (token, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reset.getToken());
            statement.setString(2, reset.getUserId());
            statement.setObject(3, OffsetDateTime.ofInstant(reset.getCreatedAt(), ZoneOffset.UTC));
            statement.setObject(4, OffsetDateTime.ofInstant(reset.getExpiresAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save password reset", ex);
        }
    }

    @Override
    public PasswordReset findByToken(String token) {
        String sql = "SELECT token, user_id, created_at, expires_at FROM identity.password_resets WHERE token = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new PasswordReset(
                    rows.getString("token"),
                    rows.getString("user_id"),
                    rows.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rows.getObject("expires_at", OffsetDateTime.class).toInstant()
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read password reset", ex);
        }
    }

    @Override
    public void delete(String token) {
        execute("DELETE FROM identity.password_resets WHERE token = ?", token);
    }

    @Override
    public void deleteByUserId(String userId) {
        execute("DELETE FROM identity.password_resets WHERE user_id = ?", userId);
    }

    private void execute(String sql, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete password reset", ex);
        }
    }
}
