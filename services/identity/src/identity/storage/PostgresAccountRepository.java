package identity.storage;

import identity.account.Account;
import identity.account.PlatformRole;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public final class PostgresAccountRepository implements AccountRepository {
    private static final String SELECT_COLS =
        "user_id, username, email, password_hash, created_at, platform_role, banned_at";

    private final DataSource dataSource;

    public PostgresAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Account account) {
        String sql =
            "INSERT INTO identity.accounts (user_id, username, email, password_hash, created_at, platform_role, banned_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getUserId());
            statement.setString(2, account.getUsername());
            statement.setString(3, account.getEmail());
            statement.setString(4, account.getPasswordHash());
            statement.setObject(5, OffsetDateTime.ofInstant(account.getCreatedAt(), ZoneOffset.UTC));
            statement.setString(6, account.getPlatformRole().name());
            setTimestamp(statement, 7, account.getBannedAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save account", ex);
        }
    }

    @Override
    public Account findByUsername(String username) {
        return findOne(
            "SELECT " + SELECT_COLS + " FROM identity.accounts WHERE username = ?",
            username
        );
    }

    @Override
    public Account findByUsernameIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return findOne(
            "SELECT " + SELECT_COLS + " FROM identity.accounts WHERE LOWER(username) = LOWER(?)",
            username
        );
    }

    @Override
    public Account findByEmail(String email) {
        return findOne(
            "SELECT " + SELECT_COLS + " FROM identity.accounts WHERE email = ?",
            email
        );
    }

    @Override
    public Account findById(String userId) {
        return findOne(
            "SELECT " + SELECT_COLS + " FROM identity.accounts WHERE user_id = ?",
            userId
        );
    }

    @Override
    public void updatePlatformRole(String userId, PlatformRole role) {
        String sql = "UPDATE identity.accounts SET platform_role = ? WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            statement.setString(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update platform role", ex);
        }
    }

    @Override
    public void updateBannedAt(String userId, Instant bannedAt) {
        String sql = "UPDATE identity.accounts SET banned_at = ? WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setTimestamp(statement, 1, bannedAt);
            statement.setString(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update ban", ex);
        }
    }

    @Override
    public void updatePasswordHash(String userId, String passwordHash) {
        String sql = "UPDATE identity.accounts SET password_hash = ? WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update password", ex);
        }
    }

    @Override
    public List<Account> findByIds(List<String> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT " + SELECT_COLS + " FROM identity.accounts WHERE user_id = ANY (?)";
        Map<String, Account> byId = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("varchar", userIds.toArray()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Account account = account(rows);
                    byId.put(account.getUserId(), account);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read accounts", ex);
        }
        List<Account> ordered = new ArrayList<>();
        for (String userId : userIds) {
            Account account = byId.get(userId);
            if (account != null) {
                ordered.add(account);
            }
        }
        return ordered;
    }

    private static Account account(ResultSet rows) throws SQLException {
        return new Account(
            rows.getString("user_id"),
            rows.getString("username"),
            rows.getString("email"),
            rows.getString("password_hash"),
            rows.getObject("created_at", OffsetDateTime.class).toInstant(),
            PlatformRole.fromStorage(rows.getString("platform_role")),
            instant(rows.getObject("banned_at", OffsetDateTime.class))
        );
    }

    private static void setTimestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private Account findOne(String sql, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return account(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read account", ex);
        }
    }
}
