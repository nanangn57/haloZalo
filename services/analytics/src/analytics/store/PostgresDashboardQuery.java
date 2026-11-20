package analytics.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import analytics.query.DashboardQuery;
import analytics.query.DayTotal;
import analytics.query.GroupTotal;
import analytics.query.UserActivity;

public final class PostgresDashboardQuery implements DashboardQuery {
    static final String SELECT_DAYS = "SELECT day, total FROM analytics.messages_by_day ORDER BY day";
    static final String SELECT_ACTIVE = "SELECT COUNT(*) FROM analytics.active_users";
    static final String SELECT_TOP =
        "SELECT group_id, total FROM analytics.group_message_totals ORDER BY total DESC, group_id LIMIT ?";
    static final String SELECT_USER =
        "SELECT logins, messages_sent, groups_created FROM analytics.user_activity WHERE user_id = ?";

    private final DataSource dataSource;

    public PostgresDashboardQuery(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DayTotal> messagesByDay() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_DAYS);
             ResultSet rows = statement.executeQuery()) {
            List<DayTotal> days = new ArrayList<>();
            while (rows.next()) {
                days.add(new DayTotal(rows.getDate("day").toLocalDate().toString(), rows.getLong("total")));
            }
            return days;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read messages by day", ex);
        }
    }

    @Override
    public long activeUsers() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read active users", ex);
        }
    }

    @Override
    public List<GroupTotal> topGroups(int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_TOP)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<GroupTotal> groups = new ArrayList<>();
                while (rows.next()) {
                    groups.add(new GroupTotal(rows.getString("group_id"), rows.getLong("total")));
                }
                return groups;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read top groups", ex);
        }
    }

    @Override
    public UserActivity activityOf(String userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_USER)) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return new UserActivity(0, 0, 0);
                }
                return new UserActivity(
                    rows.getLong("logins"),
                    rows.getLong("messages_sent"),
                    rows.getLong("groups_created")
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read user activity", ex);
        }
    }
}
