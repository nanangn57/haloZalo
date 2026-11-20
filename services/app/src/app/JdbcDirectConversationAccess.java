package app;

import identity.access.DirectConversationAccess;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lets identity SessionFilter authorize DIRECT conversation paths using messaging.direct_members.
 */
@Component
@Profile("app")
public final class JdbcDirectConversationAccess implements DirectConversationAccess {
    private final JdbcTemplate jdbc;

    public JdbcDirectConversationAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isMember(String conversationId, String userId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM messaging.direct_members WHERE conversation_id = ?::uuid AND user_id = ?::uuid",
            Integer.class,
            conversationId,
            userId
        );
        return count != null && count > 0;
    }
}
