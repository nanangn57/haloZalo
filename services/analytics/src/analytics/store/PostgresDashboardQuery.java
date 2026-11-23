package analytics.store;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import analytics.query.ConversationTotal;
import analytics.query.DashboardQuery;
import analytics.query.DateRange;
import analytics.query.DayTotal;
import analytics.query.DirectGroupSplit;
import analytics.query.FriendActivity;
import analytics.query.GroupTotal;
import analytics.query.HeatmapCell;
import analytics.query.MediaDayTotal;
import analytics.query.MediaTextSplit;
import analytics.query.MessageDayTotal;
import analytics.query.UserActivity;

public final class PostgresDashboardQuery implements DashboardQuery {
    static final String SELECT_DAYS = "SELECT day, total FROM analytics.messages_by_day ORDER BY day";
    static final String SELECT_DAYS_IN_RANGE =
        "SELECT day, total FROM analytics.messages_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date) ORDER BY day";
    static final String SELECT_ACTIVE = "SELECT COUNT(*) FROM analytics.active_users";
    static final String SELECT_TOP =
        "SELECT group_id, total FROM analytics.group_message_totals ORDER BY total DESC, group_id LIMIT ?";
    static final String SELECT_FRIENDS =
        "SELECT friends_count FROM analytics.user_activity WHERE user_id = ?";
    static final String SELECT_USER_DAY_SUM =
        "SELECT COALESCE(SUM(logins),0) AS logins, COALESCE(SUM(messages_sent),0) AS messages_sent, "
            + "COALESCE(SUM(messages_received),0) AS messages_received, COALESCE(SUM(groups_created),0) AS groups_created, "
            + "COALESCE(SUM(messages_direct),0) AS messages_direct, COALESCE(SUM(messages_group),0) AS messages_group, "
            + "COALESCE(SUM(messages_text),0) AS messages_text, COALESCE(SUM(messages_media),0) AS messages_media, "
            + "COALESCE(SUM(friend_requests_sent),0) AS friend_sent, "
            + "COALESCE(SUM(friend_requests_received),0) AS friend_received, "
            + "COALESCE(SUM(friend_requests_accepted),0) AS friend_accepted "
            + "FROM analytics.user_activity_by_day WHERE user_id = ? "
            + "AND (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_USER_DAYS =
        "SELECT day, messages_sent, messages_received FROM analytics.user_activity_by_day WHERE user_id = ? "
            + "AND (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date) ORDER BY day";
    static final String SELECT_HEATMAP =
        "SELECT weekday, hour, messages_sent FROM analytics.user_hour_weekday WHERE user_id = ? ORDER BY weekday, hour";
    static final String SELECT_TOP_CONV =
        "SELECT conversation_id, messages_sent FROM analytics.user_conversation_totals WHERE user_id = ? "
            + "ORDER BY messages_sent DESC, conversation_id LIMIT 10";
    static final String SELECT_ACTIVE_CONV =
        "SELECT COUNT(*) FROM analytics.user_conversation_totals WHERE user_id = ? AND messages_sent > 0";
    static final String SELECT_DISTINCT_ACTIVE =
        "SELECT COUNT(DISTINCT user_id) FROM analytics.active_users_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_REGISTRATIONS =
        "SELECT day, total FROM analytics.registrations_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date) ORDER BY day";
    static final String SELECT_VERIFICATIONS =
        "SELECT COALESCE(SUM(total),0) FROM analytics.verifications_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_MESSENGERS =
        "SELECT COUNT(DISTINCT user_id) FROM analytics.user_activity_by_day "
            + "WHERE messages_sent > 0 AND (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_ACTIVE_RANGE =
        "SELECT COUNT(DISTINCT user_id) FROM analytics.active_users_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_FRIEND_RANGE =
        "SELECT COALESCE(SUM(requests_sent),0) AS sent, COALESCE(SUM(requests_accepted),0) AS accepted "
            + "FROM analytics.friend_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_GROUPS_RANGE =
        "SELECT COALESCE(SUM(groups_created),0) AS groups_created, COALESCE(SUM(members_sum),0) AS members_sum "
            + "FROM analytics.groups_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date)";
    static final String SELECT_MEDIA =
        "SELECT day, type, total FROM analytics.media_by_day "
            + "WHERE (?::date IS NULL OR day >= ?::date) AND (?::date IS NULL OR day <= ?::date) ORDER BY day, type";

    private final DataSource dataSource;

    public PostgresDashboardQuery(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DayTotal> messagesByDay() {
        return dayTotals(SELECT_DAYS, DateRange.ALL);
    }

    @Override
    public List<DayTotal> messagesByDay(DateRange range) {
        return dayTotals(SELECT_DAYS_IN_RANGE, range);
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
    public UserActivity activityOf(String userId, DateRange range) {
        try (Connection connection = dataSource.getConnection()) {
            long friendsCount = 0;
            try (PreparedStatement statement = connection.prepareStatement(SELECT_FRIENDS)) {
                statement.setString(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        friendsCount = rows.getLong("friends_count");
                    }
                }
            }
            long logins = 0;
            long sent = 0;
            long received = 0;
            long groups = 0;
            long direct = 0;
            long group = 0;
            long text = 0;
            long media = 0;
            long friendSent = 0;
            long friendReceived = 0;
            long friendAccepted = 0;
            try (PreparedStatement statement = connection.prepareStatement(SELECT_USER_DAY_SUM)) {
                bindUserRange(statement, userId, range);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        logins = rows.getLong("logins");
                        sent = rows.getLong("messages_sent");
                        received = rows.getLong("messages_received");
                        groups = rows.getLong("groups_created");
                        direct = rows.getLong("messages_direct");
                        group = rows.getLong("messages_group");
                        text = rows.getLong("messages_text");
                        media = rows.getLong("messages_media");
                        friendSent = rows.getLong("friend_sent");
                        friendReceived = rows.getLong("friend_received");
                        friendAccepted = rows.getLong("friend_accepted");
                    }
                }
            }
            List<MessageDayTotal> byDay = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_USER_DAYS)) {
                bindUserRange(statement, userId, range);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        byDay.add(new MessageDayTotal(
                            rows.getDate("day").toLocalDate().toString(),
                            rows.getLong("messages_sent"),
                            rows.getLong("messages_received")
                        ));
                    }
                }
            }
            List<HeatmapCell> heatmap = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_HEATMAP)) {
                statement.setString(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        heatmap.add(new HeatmapCell(
                            rows.getInt("weekday"),
                            rows.getInt("hour"),
                            rows.getLong("messages_sent")
                        ));
                    }
                }
            }
            List<ConversationTotal> top = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_TOP_CONV)) {
                statement.setString(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        top.add(new ConversationTotal(
                            rows.getString("conversation_id"),
                            rows.getLong("messages_sent")
                        ));
                    }
                }
            }
            long activeConversations = 0;
            try (PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_CONV)) {
                statement.setString(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        activeConversations = rows.getLong(1);
                    }
                }
            }
            double acceptRate = friendSent == 0 ? 0 : (double) friendAccepted / friendSent;
            return new UserActivity(
                logins,
                sent,
                received,
                groups,
                activeConversations,
                byDay,
                new MediaTextSplit(text, media),
                new DirectGroupSplit(direct, group),
                heatmap,
                new FriendActivity(friendsCount, friendSent, friendReceived, acceptRate),
                top,
                range
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read user activity", ex);
        }
    }

    @Override
    public long distinctActive(LocalDate from, LocalDate to) {
        return countRange(SELECT_DISTINCT_ACTIVE, new DateRange(from, to));
    }

    @Override
    public List<DayTotal> registrationsByDay(DateRange range) {
        return dayTotals(SELECT_REGISTRATIONS, range);
    }

    @Override
    public long sumDayTotal(List<DayTotal> days) {
        long total = 0;
        for (DayTotal day : days) {
            total += day.getTotal();
        }
        return total;
    }

    @Override
    public long verifications(DateRange range) {
        return countRange(SELECT_VERIFICATIONS, range);
    }

    @Override
    public long messengersInRange(DateRange range) {
        return countRange(SELECT_MESSENGERS, range);
    }

    @Override
    public long activeInRange(DateRange range) {
        return countRange(SELECT_ACTIVE_RANGE, range);
    }

    @Override
    public long friendRequests(DateRange range) {
        long[] pair = friendPair(range);
        return pair[0];
    }

    @Override
    public long friendAccepted(DateRange range) {
        long[] pair = friendPair(range);
        return pair[1];
    }

    @Override
    public long groupsCreated(DateRange range) {
        long[] pair = groupsPair(range);
        return pair[0];
    }

    @Override
    public double avgMembersPerGroup(DateRange range) {
        long[] pair = groupsPair(range);
        if (pair[0] == 0) {
            return 0;
        }
        return (double) pair[1] / pair[0];
    }

    @Override
    public List<MediaDayTotal> mediaByDay(DateRange range) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_MEDIA)) {
            bindRange(statement, range);
            try (ResultSet rows = statement.executeQuery()) {
                List<MediaDayTotal> media = new ArrayList<>();
                while (rows.next()) {
                    media.add(new MediaDayTotal(
                        rows.getDate("day").toLocalDate().toString(),
                        rows.getString("type"),
                        rows.getLong("total")
                    ));
                }
                return media;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read media by day", ex);
        }
    }

    private List<DayTotal> dayTotals(String sql, DateRange range) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (sql.contains("?::date")) {
                bindRange(statement, range);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<DayTotal> days = new ArrayList<>();
                while (rows.next()) {
                    days.add(new DayTotal(rows.getDate("day").toLocalDate().toString(), rows.getLong("total")));
                }
                return days;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read day totals", ex);
        }
    }

    private long countRange(String sql, DateRange range) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, range);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not count range", ex);
        }
    }

    private long[] friendPair(DateRange range) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_FRIEND_RANGE)) {
            bindRange(statement, range);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return new long[] {0, 0};
                }
                return new long[] {rows.getLong("sent"), rows.getLong("accepted")};
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read friend totals", ex);
        }
    }

    private long[] groupsPair(DateRange range) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_GROUPS_RANGE)) {
            bindRange(statement, range);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return new long[] {0, 0};
                }
                return new long[] {rows.getLong("groups_created"), rows.getLong("members_sum")};
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read group totals", ex);
        }
    }

    private static void bindRange(PreparedStatement statement, DateRange range) throws SQLException {
        Date from = range.from() == null ? null : Date.valueOf(range.from());
        Date to = range.to() == null ? null : Date.valueOf(range.to());
        statement.setDate(1, from);
        statement.setDate(2, from);
        statement.setDate(3, to);
        statement.setDate(4, to);
    }

    private static void bindUserRange(PreparedStatement statement, String userId, DateRange range) throws SQLException {
        statement.setString(1, userId);
        Date from = range.from() == null ? null : Date.valueOf(range.from());
        Date to = range.to() == null ? null : Date.valueOf(range.to());
        statement.setDate(2, from);
        statement.setDate(3, from);
        statement.setDate(4, to);
        statement.setDate(5, to);
    }
}
