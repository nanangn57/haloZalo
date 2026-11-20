package member;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Temporary while messaging runs alone against the shared DB. Disabled in profile {@code app}
 * where {@code IdentityGroupMembers} adapts MembershipLookup in-process.
 */
@Component
@Primary
@Profile("!app")
public final class SharedDbGroupMembers implements GroupMembers {
    private final JdbcTemplate jdbc;

    public SharedDbGroupMembers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isMember(String groupId, String userId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM identity.memberships WHERE group_id = ? AND user_id = ?",
            Integer.class,
            groupId,
            userId
        );
        return count != null && count > 0;
    }

    @Override
    public Set<String> memberIds(String groupId) {
        List<String> rows = jdbc.query(
            "SELECT user_id FROM identity.memberships WHERE group_id = ?",
            (rs, rowNum) -> rs.getString(1),
            groupId
        );
        return Set.copyOf(rows);
    }

    @Override
    public Set<String> groupIdsOf(String userId) {
        List<String> rows = jdbc.query(
            "SELECT group_id FROM identity.memberships WHERE user_id = ?",
            (rs, rowNum) -> rs.getString(1),
            userId
        );
        return new HashSet<>(rows);
    }
}
