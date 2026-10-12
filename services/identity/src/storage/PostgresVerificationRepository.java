package storage;

import verification.EmailVerification;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

@Component
public final class PostgresVerificationRepository implements VerificationRepository {
    private final DataSource dataSource;

    public PostgresVerificationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(EmailVerification verification) {
        String sql = "INSERT INTO identity.verifications (token, username, email, password_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, verification.getToken());
            statement.setString(2, verification.getUsername());
            statement.setString(3, verification.getEmail());
            statement.setString(4, verification.getPasswordHash());
            statement.setObject(5, OffsetDateTime.ofInstant(verification.getCreatedAt(), ZoneOffset.UTC));
            statement.setObject(6, OffsetDateTime.ofInstant(verification.getExpiresAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save verification", ex);
        }
    }

    @Override
    public EmailVerification findByToken(String token) {
        return findOne("SELECT token, username, email, password_hash, created_at, expires_at FROM identity.verifications WHERE token = ?", token);
    }

    @Override
    public EmailVerification findByUsername(String username) {
        return findOne("SELECT token, username, email, password_hash, created_at, expires_at FROM identity.verifications WHERE username = ?", username);
    }

    @Override
    public EmailVerification findByEmail(String email) {
        return findOne("SELECT token, username, email, password_hash, created_at, expires_at FROM identity.verifications WHERE email = ?", email);
    }

    @Override
    public void delete(String token) {
        String sql = "DELETE FROM identity.verifications WHERE token = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete verification", ex);
        }
    }

    private EmailVerification findOne(String sql, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new EmailVerification(
                    rows.getString("token"),
                    rows.getString("username"),
                    rows.getString("email"),
                    rows.getString("password_hash"),
                    rows.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rows.getObject("expires_at", OffsetDateTime.class).toInstant()
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read verification", ex);
        }
    }
}
