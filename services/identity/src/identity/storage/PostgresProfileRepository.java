package identity.storage;

import identity.account.Profile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

@Component
public final class PostgresProfileRepository implements ProfileRepository {
    private final DataSource dataSource;

    public PostgresProfileRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Profile find(String userId) {
        String sql = "SELECT user_id, display_name, avatar, bio FROM identity.profiles WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new Profile(rows.getString("user_id"), rows.getString("display_name"), rows.getString("avatar"), rows.getString("bio"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read profile", ex);
        }
    }

    @Override
    public void save(Profile profile) {
        String sql = "INSERT INTO identity.profiles (user_id, display_name, avatar, bio) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT (user_id) DO UPDATE SET display_name = EXCLUDED.display_name, "
            + "avatar = EXCLUDED.avatar, bio = EXCLUDED.bio";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.getUserId());
            statement.setString(2, profile.getDisplayName());
            statement.setString(3, profile.getAvatar());
            statement.setString(4, profile.getBio());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save profile", ex);
        }
    }
}
