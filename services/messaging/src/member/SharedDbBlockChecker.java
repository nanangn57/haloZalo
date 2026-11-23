package member;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Temporary while messaging runs alone against the shared DB. Disabled in profile {@code app}
 * where {@code IdentityBlockChecker} adapts BlockLookup in-process.
 */
@Component
@Primary
@Profile("!app")
public final class SharedDbBlockChecker implements BlockChecker {
    private final JdbcTemplate jdbc;

    public SharedDbBlockChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isBlocked(String userId, String otherUserId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM identity.blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?)",
            Integer.class,
            userId, otherUserId, otherUserId, userId
        );
        return count != null && count > 0;
    }
}
