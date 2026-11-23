package identity.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

@Component
public final class PostgresBlockRepository implements BlockRepository {
    private final DataSource dataSource;

    public PostgresBlockRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(String blockerId, String blockedId) {
        String sql = "INSERT INTO identity.blocks (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blockerId);
            statement.setString(2, blockedId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save block", ex);
        }
    }

    @Override
    public void delete(String blockerId, String blockedId) {
        String sql = "DELETE FROM identity.blocks WHERE blocker_id = ? AND blocked_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blockerId);
            statement.setString(2, blockedId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete block", ex);
        }
    }

    @Override
    public boolean exists(String blockerId, String blockedId) {
        String sql = "SELECT 1 FROM identity.blocks WHERE blocker_id = ? AND blocked_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blockerId);
            statement.setString(2, blockedId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read block", ex);
        }
    }

    @Override
    public List<String> blockedIds(String blockerId) {
        String sql = "SELECT blocked_id FROM identity.blocks WHERE blocker_id = ? ORDER BY blocked_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blockerId);
            List<String> ids = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getString("blocked_id"));
                }
            }
            return ids;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not list blocks", ex);
        }
    }
}
