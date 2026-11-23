package app;

import identity.access.FriendshipLookup;
import member.FriendshipChecker;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-process adapter: messaging FriendshipChecker → identity FriendshipLookup. No cross-schema JDBC.
 */
@Component
@Primary
@Profile("app")
public final class IdentityFriendshipChecker implements FriendshipChecker {
    private final FriendshipLookup friendships;

    public IdentityFriendshipChecker(FriendshipLookup friendships) {
        this.friendships = friendships;
    }

    @Override
    public boolean areFriends(String userId, String otherUserId) {
        return friendships.areFriends(userId, otherUserId);
    }
}
