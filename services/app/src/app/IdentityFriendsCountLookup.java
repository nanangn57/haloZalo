package app;

import analytics.query.FriendsCountLookup;
import identity.storage.FriendRepository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Live friends.count for personal dashboard — reads identity friendships, not the
 * analytics counter (which drifts when seed/reconcile/unfriend skip events).
 */
@Component
@Profile("app")
public final class IdentityFriendsCountLookup implements FriendsCountLookup {
    private final FriendRepository friends;

    public IdentityFriendsCountLookup(FriendRepository friends) {
        this.friends = friends;
    }

    @Override
    public long currentCount(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        return friends.friendIds(userId).size();
    }
}
