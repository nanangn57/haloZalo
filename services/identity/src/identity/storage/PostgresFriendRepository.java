package identity.storage;

import identity.friend.FriendRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

@Component
public final class PostgresFriendRepository implements FriendRepository {
    private final DataSource dataSource;

    public PostgresFriendRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveRequest(FriendRequest request) {
        String sql = "INSERT INTO identity.friend_requests (from_user_id, to_user_id) VALUES (?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.getFromUserId());
            statement.setString(2, request.getToUserId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) {
                throw new DuplicateKeyException("Friend request already exists", ex);
            }
            throw new IllegalStateException("Could not save friend request", ex);
        }
    }

    @Override
    public FriendRequest findRequest(String fromUserId, String toUserId) {
        String sql = "SELECT from_user_id, to_user_id FROM identity.friend_requests WHERE from_user_id = ? AND to_user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fromUserId);
            statement.setString(2, toUserId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new FriendRequest(rows.getString("from_user_id"), rows.getString("to_user_id"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read friend request", ex);
        }
    }

    @Override
    public void deleteRequest(String fromUserId, String toUserId) {
        String sql = "DELETE FROM identity.friend_requests WHERE from_user_id = ? AND to_user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fromUserId);
            statement.setString(2, toUserId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete friend request", ex);
        }
    }

    @Override
    public boolean areFriends(String userId, String friendId) {
        String sql = "SELECT 1 FROM identity.friendships WHERE user_id = ? AND friend_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, friendId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read friendship", ex);
        }
    }

    @Override
    public void saveFriendship(String userId, String friendId) {
        String sql = "INSERT INTO identity.friendships (user_id, friend_id) VALUES (?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, friendId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save friendship", ex);
        }
    }

    @Override
    public List<String> friendIds(String userId) {
        String sql = "SELECT friend_id FROM identity.friendships WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            List<String> ids = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getString("friend_id"));
                }
            }
            return ids;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not list friends", ex);
        }
    }
}
