package identity.access;

import identity.storage.FriendRepository;

import org.springframework.stereotype.Component;

@Component
public final class RepositoryFriendshipLookup implements FriendshipLookup {
    private final FriendRepository friends;

    public RepositoryFriendshipLookup(FriendRepository friends) {
        this.friends = friends;
    }

    @Override
    public boolean areFriends(String userId, String otherUserId) {
        if (blank(userId) || blank(otherUserId)) {
            return false;
        }
        return friends.areFriends(userId, otherUserId) || friends.areFriends(otherUserId, userId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
