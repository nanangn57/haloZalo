package storage;

import group.Membership;
import group.Role;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

@Component
public final class PostgresMembershipRepository implements MembershipRepository {
    private final DataSource dataSource;

    public PostgresMembershipRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Membership membership) {
        String sql = "INSERT INTO identity.memberships (group_id, user_id, joined_at, role) VALUES (?, ?, ?, ?)";
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, membership.getGroupId());
            statement.setString(2, membership.getUserId());
            statement.setObject(3, OffsetDateTime.ofInstant(membership.getJoinedAt(), ZoneOffset.UTC));
            statement.setString(4, membership.getRole().name());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) {
                throw new DuplicateKeyException("Membership already exists", ex);
            }
            throw new IllegalStateException("Could not save membership", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
    @Override
    public void delete(String groupId, String userId) {
        String sql = "DELETE FROM identity.memberships WHERE group_id = ? AND user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, groupId);
            statement.setString(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete membership", ex);
        }
    }
    @Override
    public Membership find(String groupId, String userId) {
        String sql = "SELECT group_id, user_id, joined_at, role FROM identity.memberships WHERE group_id = ? AND user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, groupId);
            statement.setString(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return read(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read membership", ex);
        }
    }
    @Override
    public List<Membership> listByUser(String userId) {
        String sql = "SELECT group_id, user_id, joined_at, role FROM identity.memberships WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Membership> memberships = new ArrayList<>();
                while (rows.next()) {
                    memberships.add(read(rows));
                }
                return memberships;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not list memberships", ex);
        }
    }
    private static Membership read(ResultSet rows) throws SQLException {
        return new Membership(
            rows.getString("group_id"),
            rows.getString("user_id"),
            rows.getObject("joined_at", OffsetDateTime.class).toInstant(),
            Role.valueOf(rows.getString("role"))
        );
    }
}
