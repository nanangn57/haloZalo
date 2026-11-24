package app;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import analytics.record.ActivityRecorder;
import analytics.record.ActivityTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Idempotent bridge when identity rows exist but analytics never saw the events
 * (SQL seed, deploy before recorder, wiped analytics tables).
 * Deterministic event ids keep re-runs from double-counting totals.
 */
@Component
@Profile("app")
@Order(100)
public final class IdentityAnalyticsReconcile implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(IdentityAnalyticsReconcile.class);

    private final DataSource dataSource;
    private final ActivityRecorder activities;

    public IdentityAnalyticsReconcile(DataSource dataSource, ActivityRecorder activities) {
        this.dataSource = dataSource;
        this.activities = activities;
    }

    @Override
    public void run(ApplicationArguments args) {
        int registered = 0;
        int friends = 0;
        int groups = 0;
        int syncedFriends = 0;
        try (Connection connection = dataSource.getConnection()) {
            registered = reconcileAccounts(connection);
            rebuildRegistrationDays(connection);
            friends = reconcileFriends(connection);
            syncedFriends = syncFriendsCounts(connection);
            groups = reconcileGroups(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not reconcile identity → analytics", ex);
        }
        if (registered > 0 || friends > 0 || groups > 0 || syncedFriends > 0) {
            log.info(
                "Identity analytics reconcile applied accountEvents={} friendEvents={} groupEvents={} friendsCountRows={}",
                registered,
                friends,
                groups,
                syncedFriends
            );
        }
    }

    private int reconcileAccounts(Connection connection) throws SQLException {
        String sql = "SELECT user_id, created_at FROM identity.accounts";
        int applied = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String userId = rows.getString("user_id");
                Instant at = rows.getTimestamp("created_at").toInstant();
                if (record(ActivityTypes.REGISTERED, deterministicId("reg", userId), at, userPayload(userId))) {
                    applied++;
                }
                // Accounts table only holds activated users (email verify or immediate).
                if (record(ActivityTypes.VERIFIED, deterministicId("ver", userId), at, userPayload(userId))) {
                    applied++;
                }
            }
        }
        return applied;
    }

    private void rebuildRegistrationDays(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM analytics.registrations_by_day");
            statement.executeUpdate(
                "INSERT INTO analytics.registrations_by_day (day, total) "
                    + "SELECT day, COUNT(*) FROM analytics.user_registrations GROUP BY day"
            );
            statement.executeUpdate("DELETE FROM analytics.verifications_by_day");
            statement.executeUpdate(
                "INSERT INTO analytics.verifications_by_day (day, total) "
                    + "SELECT day, COUNT(*) FROM analytics.user_verifications GROUP BY day"
            );
        }
    }

    /**
     * Absolute snapshot of current friendships into analytics.user_activity.friends_count.
     * Event replay alone drifts (seed rows, skipped reconcile when friend_by_day exists, unfriend).
     */
    private int syncFriendsCounts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE analytics.user_activity SET friends_count = 0");
        }
        String sql =
            "INSERT INTO analytics.user_activity "
                + "(user_id, logins, messages_sent, messages_received, groups_created, friends_count) "
                + "SELECT user_id, 0, 0, 0, 0, COUNT(*) FROM identity.friendships GROUP BY user_id "
                + "ON CONFLICT (user_id) DO UPDATE SET friends_count = EXCLUDED.friends_count";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        }
    }

    private int reconcileFriends(Connection connection) throws SQLException {
        // Always attempt; deterministic event ids + recorded_events keep re-runs idempotent.
        // Do not skip when friend_by_day already has rows — that left personal sent/received at 0
        // for seed / pre-recorder friendships (e.g. anh ↔ huy).
        int applied = 0;
        String pending = "SELECT from_user_id, to_user_id FROM identity.friend_requests";
        try (PreparedStatement statement = connection.prepareStatement(pending);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String from = rows.getString("from_user_id");
                String to = rows.getString("to_user_id");
                if (record(
                    ActivityTypes.FRIEND_REQUEST_SENT,
                    deterministicId("frs", from + ":" + to),
                    Instant.now(),
                    friendPayload(from, to)
                )) {
                    applied++;
                }
            }
        }
        String accepted =
            "SELECT user_id, friend_id FROM identity.friendships WHERE user_id < friend_id";
        try (PreparedStatement statement = connection.prepareStatement(accepted);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String from = rows.getString("user_id");
                String to = rows.getString("friend_id");
                Instant at = Instant.now();
                // Same "frs"/"fra" ids as LoggingEventPublisher so live + reconcile never double-count.
                if (record(
                    ActivityTypes.FRIEND_REQUEST_SENT,
                    deterministicId("frs", from + ":" + to),
                    at,
                    friendPayload(from, to)
                )) {
                    applied++;
                }
                if (record(
                    ActivityTypes.FRIEND_REQUEST_ACCEPTED,
                    deterministicId("fra", from + ":" + to),
                    at,
                    friendPayload(from, to)
                )) {
                    applied++;
                }
            }
        }
        return applied;
    }

    private int reconcileGroups(Connection connection) throws SQLException {
        // Always attempt; deterministic "grp" event ids + recorded_events / known_groups keep
        // re-runs idempotent. Do not skip when groups_by_day already has rows — that left
        // personal groupsCreated at 0 for groups created before the recorder (cloud seed/QA).
        String sql =
            "SELECT g.group_id, g.owner_id, g.created_at, "
                + "(SELECT COUNT(*) FROM identity.memberships m WHERE m.group_id = g.group_id) AS members "
                + "FROM identity.groups g";
        int applied = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String groupId = rows.getString("group_id");
                if (groupAlreadyCounted(connection, groupId)) {
                    continue;
                }
                String ownerId = rows.getString("owner_id");
                Instant at = rows.getTimestamp("created_at").toInstant();
                int members = Math.max(1, rows.getInt("members"));
                String payload = "{\"groupId\":\"" + groupId + "\",\"ownerId\":\"" + ownerId
                    + "\",\"memberCount\":" + members + "}";
                if (record(ActivityTypes.GROUP_CREATED, deterministicId("grp", groupId), at, payload)) {
                    applied++;
                }
            }
        }
        return applied;
    }

    /** True when analytics already counted this group (live random id or prior reconcile). */
    private static boolean groupAlreadyCounted(Connection connection, String groupId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM analytics.known_groups WHERE group_id = ?"
        )) {
            statement.setString(1, groupId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean record(String type, String eventId, Instant occurredAt, String payload) {
        if (alreadyRecorded(eventId)) {
            return false;
        }
        String envelope = "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"type\":\"" + type + "\","
            + "\"occurredAt\":\"" + occurredAt + "\","
            + "\"producer\":\"app.reconcile\","
            + "\"payload\":" + payload
            + "}";
        activities.record(envelope);
        return alreadyRecorded(eventId);
    }

    private boolean alreadyRecorded(String eventId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM analytics.recorded_events WHERE event_id = ?"
             )) {
            statement.setString(1, eventId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private static String userPayload(String userId) {
        return "{\"userId\":\"" + userId + "\"}";
    }

    private static String friendPayload(String from, String to) {
        return "{\"fromUserId\":\"" + from + "\",\"toUserId\":\"" + to + "\"}";
    }

    static String deterministicId(String kind, String key) {
        return UUID.nameUUIDFromBytes(("halozalo:" + kind + ":" + key).getBytes(StandardCharsets.UTF_8))
            .toString();
    }
}
