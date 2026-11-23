package member;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Temporary while messaging runs alone against the shared DB. Disabled in profile {@code app}
 * where {@code IdentityFriendshipChecker} adapts FriendshipLookup in-process.
 */
@Component
@Primary
@Profile("!app")
public final class SharedDbFriendshipChecker implements FriendshipChecker {
    private final JdbcTemplate jdbc;

    public SharedDbFriendshipChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean areFriends(String userId, String otherUserId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM identity.friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)",
            Integer.class,
            userId, otherUserId, otherUserId, userId
        );
        return count != null && count > 0;
    }
}
