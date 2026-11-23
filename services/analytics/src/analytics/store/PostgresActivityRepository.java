package analytics.store;

import analytics.record.ActivityTypes;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

public final class PostgresActivityRepository implements ActivityRepository, ActivityBreakdown {
    static final String INSERT_EVENT =
        "INSERT INTO analytics.recorded_events (event_id, type, occurred_at) VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING";
    static final String INCREMENT =
        "INSERT INTO analytics.totals (type, total) VALUES (?, 1) ON CONFLICT (type) DO UPDATE SET total = analytics.totals.total + 1";
    static final String COUNT = "SELECT total FROM analytics.totals WHERE type = ?";
    static final String INSERT_DAY =
        "INSERT INTO analytics.messages_by_day (day, total) VALUES (?, 1) ON CONFLICT (day) DO UPDATE SET total = analytics.messages_by_day.total + 1";
    static final String INSERT_ACTIVE =
        "INSERT INTO analytics.active_users (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING";
    static final String INSERT_ACTIVE_DAY =
        "INSERT INTO analytics.active_users_by_day (day, user_id) VALUES (?, ?) ON CONFLICT (day, user_id) DO NOTHING";
    static final String INSERT_GROUP =
        "INSERT INTO analytics.known_groups (group_id, member_count) VALUES (?, ?) ON CONFLICT (group_id) DO UPDATE SET "
            + "member_count = GREATEST(analytics.known_groups.member_count, EXCLUDED.member_count)";
    static final String GROUP_KNOWN = "SELECT 1 FROM analytics.known_groups WHERE group_id = ?";
    static final String INSERT_GROUP_TOTAL =
        "INSERT INTO analytics.group_message_totals (group_id, total) VALUES (?, 1) ON CONFLICT (group_id) DO UPDATE SET total = analytics.group_message_totals.total + 1";
    static final String INSERT_USER =
        "INSERT INTO analytics.user_activity (user_id, logins, messages_sent, messages_received, groups_created, friends_count) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET "
            + "logins = analytics.user_activity.logins + EXCLUDED.logins, "
            + "messages_sent = analytics.user_activity.messages_sent + EXCLUDED.messages_sent, "
            + "messages_received = analytics.user_activity.messages_received + EXCLUDED.messages_received, "
            + "groups_created = analytics.user_activity.groups_created + EXCLUDED.groups_created, "
            + "friends_count = analytics.user_activity.friends_count + EXCLUDED.friends_count";
    static final String INSERT_USER_DAY =
        "INSERT INTO analytics.user_activity_by_day ("
            + "user_id, day, logins, messages_sent, messages_received, messages_direct, messages_group, "
            + "messages_text, messages_media, groups_created, friend_requests_sent, friend_requests_received, "
            + "friend_requests_accepted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (user_id, day) DO UPDATE SET "
            + "logins = analytics.user_activity_by_day.logins + EXCLUDED.logins, "
            + "messages_sent = analytics.user_activity_by_day.messages_sent + EXCLUDED.messages_sent, "
            + "messages_received = analytics.user_activity_by_day.messages_received + EXCLUDED.messages_received, "
            + "messages_direct = analytics.user_activity_by_day.messages_direct + EXCLUDED.messages_direct, "
            + "messages_group = analytics.user_activity_by_day.messages_group + EXCLUDED.messages_group, "
            + "messages_text = analytics.user_activity_by_day.messages_text + EXCLUDED.messages_text, "
            + "messages_media = analytics.user_activity_by_day.messages_media + EXCLUDED.messages_media, "
            + "groups_created = analytics.user_activity_by_day.groups_created + EXCLUDED.groups_created, "
            + "friend_requests_sent = analytics.user_activity_by_day.friend_requests_sent + EXCLUDED.friend_requests_sent, "
            + "friend_requests_received = analytics.user_activity_by_day.friend_requests_received + EXCLUDED.friend_requests_received, "
            + "friend_requests_accepted = analytics.user_activity_by_day.friend_requests_accepted + EXCLUDED.friend_requests_accepted";
    static final String INSERT_HEATMAP =
        "INSERT INTO analytics.user_hour_weekday (user_id, weekday, hour, messages_sent) VALUES (?, ?, ?, 1) "
            + "ON CONFLICT (user_id, weekday, hour) DO UPDATE SET "
            + "messages_sent = analytics.user_hour_weekday.messages_sent + 1";
    static final String INSERT_CONVERSATION =
        "INSERT INTO analytics.user_conversation_totals (user_id, conversation_id, messages_sent) VALUES (?, ?, 1) "
            + "ON CONFLICT (user_id, conversation_id) DO UPDATE SET "
            + "messages_sent = analytics.user_conversation_totals.messages_sent + 1";
    static final String INSERT_REGISTRATION =
        "INSERT INTO analytics.registrations_by_day (day, total) VALUES (?, 1) ON CONFLICT (day) DO UPDATE SET "
            + "total = analytics.registrations_by_day.total + 1";
    static final String INSERT_VERIFICATION =
        "INSERT INTO analytics.verifications_by_day (day, total) VALUES (?, 1) ON CONFLICT (day) DO UPDATE SET "
            + "total = analytics.verifications_by_day.total + 1";
    static final String INSERT_FRIEND_DAY =
        "INSERT INTO analytics.friend_by_day (day, requests_sent, requests_accepted) VALUES (?, ?, ?) "
            + "ON CONFLICT (day) DO UPDATE SET "
            + "requests_sent = analytics.friend_by_day.requests_sent + EXCLUDED.requests_sent, "
            + "requests_accepted = analytics.friend_by_day.requests_accepted + EXCLUDED.requests_accepted";
    static final String INSERT_GROUPS_DAY =
        "INSERT INTO analytics.groups_by_day (day, groups_created, members_sum) VALUES (?, 1, ?) "
            + "ON CONFLICT (day) DO UPDATE SET "
            + "groups_created = analytics.groups_by_day.groups_created + 1, "
            + "members_sum = analytics.groups_by_day.members_sum + EXCLUDED.members_sum";
    static final String INSERT_MEDIA_DAY =
        "INSERT INTO analytics.media_by_day (day, type, total) VALUES (?, ?, 1) ON CONFLICT (day, type) DO UPDATE SET "
            + "total = analytics.media_by_day.total + 1";

    private final DataSource dataSource;

    public PostgresActivityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean recordIfNew(String eventId, String type, Instant occurredAt) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT)) {
                    statement.setString(1, eventId);
                    statement.setString(2, type);
                    statement.setObject(3, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    try (PreparedStatement statement = connection.prepareStatement(INCREMENT)) {
                        statement.setString(1, type);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return inserted == 1;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not record activity", ex);
        }
    }

    @Override
    public long count(String type) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT)) {
            statement.setString(1, type);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return 0;
                }
                return rows.getLong("total");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read activity total", ex);
        }
    }

    @Override
    public void note(ActivityNote note) {
        try (Connection connection = dataSource.getConnection()) {
            LocalDate day = note.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate();
            if (ActivityTypes.REGISTERED.equals(note.type())) {
                bumpDayTotal(connection, INSERT_REGISTRATION, day);
            } else if (ActivityTypes.VERIFIED.equals(note.type())) {
                bumpDayTotal(connection, INSERT_VERIFICATION, day);
            } else if (ActivityTypes.LOGGED_IN.equals(note.type())) {
                markActive(connection, note.actorId(), day);
                addUser(connection, note.actorId(), 1, 0, 0, 0, 0);
                addUserDay(connection, note.actorId(), day, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            } else if (ActivityTypes.GROUP_CREATED.equals(note.type())) {
                int members = note.memberCount() == null || note.memberCount() < 1 ? 1 : note.memberCount();
                rememberGroup(connection, note.subjectId(), members);
                addUser(connection, note.actorId(), 0, 0, 0, 1, 0);
                addUserDay(connection, note.actorId(), day, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
                addGroupsDay(connection, day, members);
            } else if (ActivityTypes.FRIEND_REQUEST_SENT.equals(note.type())) {
                addUserDay(connection, note.fromUserId(), day, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0);
                addUserDay(connection, note.toUserId(), day, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
                addFriendDay(connection, day, 1, 0);
            } else if (ActivityTypes.FRIEND_REQUEST_ACCEPTED.equals(note.type())) {
                addUser(connection, note.fromUserId(), 0, 0, 0, 0, 1);
                addUser(connection, note.toUserId(), 0, 0, 0, 0, 1);
                addUserDay(connection, note.fromUserId(), day, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
                addUserDay(connection, note.toUserId(), day, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
                addFriendDay(connection, day, 0, 1);
            } else if (ActivityTypes.MESSAGE_CREATED.equals(note.type())) {
                addDay(connection, day);
                markActive(connection, note.actorId(), day);
                boolean direct = "DIRECT".equalsIgnoreCase(note.conversationType());
                boolean group = "GROUP".equalsIgnoreCase(note.conversationType());
                boolean text = ActivityTypes.isTextLike(note.contentType());
                boolean media = ActivityTypes.isMediaMessage(note.contentType());
                addUser(connection, note.actorId(), 0, 1, 0, 0, 0);
                addUserDay(
                    connection,
                    note.actorId(),
                    day,
                    0,
                    1,
                    0,
                    direct ? 1 : 0,
                    group ? 1 : 0,
                    text ? 1 : 0,
                    media ? 1 : 0,
                    0,
                    0,
                    0,
                    0
                );
                addHeatmap(connection, note.actorId(), note.occurredAt());
                addConversation(connection, note.actorId(), note.subjectId());
                if (knownGroup(connection, note.subjectId()) || group) {
                    addGroupTotal(connection, note.subjectId());
                }
                for (String recipient : note.recipientIds()) {
                    markActive(connection, recipient, day);
                    addUser(connection, recipient, 0, 0, 1, 0, 0);
                    addUserDay(connection, recipient, day, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
                }
            } else if (ActivityTypes.MEDIA_UPLOADED.equals(note.type())) {
                String mediaType = note.contentType() == null ? "IMAGE" : note.contentType();
                addMediaDay(connection, day, mediaType);
                markActive(connection, note.actorId(), day);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not record activity breakdown", ex);
        }
    }

    private static void addDay(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DAY)) {
            statement.setDate(1, Date.valueOf(day));
            statement.executeUpdate();
        }
    }

    private static void bumpDayTotal(Connection connection, String sql, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(day));
            statement.executeUpdate();
        }
    }

    private static void markActive(Connection connection, String userId, LocalDate day) throws SQLException {
        if (userId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ACTIVE)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ACTIVE_DAY)) {
            statement.setDate(1, Date.valueOf(day));
            statement.setString(2, userId);
            statement.executeUpdate();
        }
    }

    private static void rememberGroup(Connection connection, String groupId, int memberCount) throws SQLException {
        if (groupId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_GROUP)) {
            statement.setString(1, groupId);
            statement.setLong(2, memberCount);
            statement.executeUpdate();
        }
    }

    private static boolean knownGroup(Connection connection, String groupId) throws SQLException {
        if (groupId == null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(GROUP_KNOWN)) {
            statement.setString(1, groupId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void addGroupTotal(Connection connection, String groupId) throws SQLException {
        if (groupId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_GROUP_TOTAL)) {
            statement.setString(1, groupId);
            statement.executeUpdate();
        }
    }

    private static void addUser(
        Connection connection,
        String userId,
        long logins,
        long messagesSent,
        long messagesReceived,
        long groups,
        long friends
    ) throws SQLException {
        if (userId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, userId);
            statement.setLong(2, logins);
            statement.setLong(3, messagesSent);
            statement.setLong(4, messagesReceived);
            statement.setLong(5, groups);
            statement.setLong(6, friends);
            statement.executeUpdate();
        }
    }

    private static void addUserDay(
        Connection connection,
        String userId,
        LocalDate day,
        long logins,
        long sent,
        long received,
        long direct,
        long group,
        long text,
        long media,
        long groupsCreated,
        long friendSent,
        long friendReceived,
        long friendAccepted
    ) throws SQLException {
        if (userId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER_DAY)) {
            statement.setString(1, userId);
            statement.setDate(2, Date.valueOf(day));
            statement.setLong(3, logins);
            statement.setLong(4, sent);
            statement.setLong(5, received);
            statement.setLong(6, direct);
            statement.setLong(7, group);
            statement.setLong(8, text);
            statement.setLong(9, media);
            statement.setLong(10, groupsCreated);
            statement.setLong(11, friendSent);
            statement.setLong(12, friendReceived);
            statement.setLong(13, friendAccepted);
            statement.executeUpdate();
        }
    }

    private static void addHeatmap(Connection connection, String userId, Instant occurredAt) throws SQLException {
        if (userId == null) {
            return;
        }
        OffsetDateTime at = occurredAt.atOffset(ZoneOffset.UTC);
        int weekday = at.getDayOfWeek().getValue() - 1; // Monday = 0
        int hour = at.getHour();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_HEATMAP)) {
            statement.setString(1, userId);
            statement.setInt(2, weekday);
            statement.setInt(3, hour);
            statement.executeUpdate();
        }
    }

    private static void addConversation(Connection connection, String userId, String conversationId) throws SQLException {
        if (userId == null || conversationId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CONVERSATION)) {
            statement.setString(1, userId);
            statement.setString(2, conversationId);
            statement.executeUpdate();
        }
    }

    private static void addFriendDay(Connection connection, LocalDate day, long sent, long accepted) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_FRIEND_DAY)) {
            statement.setDate(1, Date.valueOf(day));
            statement.setLong(2, sent);
            statement.setLong(3, accepted);
            statement.executeUpdate();
        }
    }

    private static void addGroupsDay(Connection connection, LocalDate day, int members) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_GROUPS_DAY)) {
            statement.setDate(1, Date.valueOf(day));
            statement.setLong(2, members);
            statement.executeUpdate();
        }
    }

    private static void addMediaDay(Connection connection, LocalDate day, String type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MEDIA_DAY)) {
            statement.setDate(1, Date.valueOf(day));
            statement.setString(2, type);
            statement.executeUpdate();
        }
    }
}
