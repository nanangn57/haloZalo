package identity.storage;

import identity.group.Group;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

@Component
public final class PostgresGroupRepository implements GroupRespository {
    private final DataSource dataSource;

    public PostgresGroupRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Group group) {
        String sql = "INSERT INTO identity.groups (group_id, name, description, created_at, owner_id) VALUES (?, ?, ?, ?, ?)";
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, group.getGroupId());
            statement.setString(2, group.getGroupName());
            statement.setString(3, group.getGroupDescription());
            statement.setObject(4, OffsetDateTime.ofInstant(group.getGroupCreatedAt(), ZoneOffset.UTC));
            statement.setString(5, group.getGroupOwnerId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save group", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public Group findById(String groupId) {
        String sql = "SELECT group_id, name, description, created_at, owner_id FROM identity.groups WHERE group_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, groupId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new Group(
                    rows.getString("group_id"),
                    rows.getString("name"),
                    rows.getString("description"),
                    rows.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rows.getString("owner_id")
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read group", ex);
        }
    }
}
