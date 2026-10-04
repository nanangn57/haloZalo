package storage;

import account.Account;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

@Component
public final class MySqlAccountRepository implements AccountRepository {
    private final DataSource dataSource;

    public MySqlAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Account account) {
        String sql = "INSERT INTO identity.accounts (user_id, username, email, password_hash) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getUserId());
            statement.setString(2, account.getUsername());
            statement.setString(3, account.getEmail());
            statement.setString(4, account.getPasswordHash());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save account", ex);
        }
    }

    @Override
    public Account findByUsername(String username) {
        return findOne("SELECT user_id, username, email, password_hash FROM identity.accounts WHERE username = ?", username);
    }

    @Override
    public Account findByEmail(String email) {
        return findOne("SELECT user_id, username, email, password_hash FROM identity.accounts WHERE email = ?", email);
    }

    @Override
    public Account findById(String userId) {
        return findOne("SELECT user_id, username, email, password_hash FROM identity.accounts WHERE user_id = ?", userId);
    }

    private Account findOne(String sql, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new Account(
                    rows.getString("user_id"),
                    rows.getString("username"),
                    rows.getString("email"),
                    rows.getString("password_hash")
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read account", ex);
        }
    }
}
