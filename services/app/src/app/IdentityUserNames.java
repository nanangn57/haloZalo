package app;

import identity.auth.UserSummaryList;
import identity.service.UserSearchService;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-process adapter: names come from identity's user lookup. No cross-schema JDBC.
 */
@Component
@Profile("app")
public final class IdentityUserNames implements UserNames {
    static final String UNKNOWN = "Someone";

    private final UserSearchService users;

    public IdentityUserNames(UserSearchService users) {
        this.users = users;
    }

    @Override
    public String displayNameOf(String userId) {
        if (userId == null) {
            return UNKNOWN;
        }
        List<UserSummaryList.UserSummary> found = users.summaries(userId);
        if (found.isEmpty()) {
            return UNKNOWN;
        }
        UserSummaryList.UserSummary user = found.get(0);
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getUsername() == null || user.getUsername().isBlank() ? UNKNOWN : user.getUsername();
    }
}
