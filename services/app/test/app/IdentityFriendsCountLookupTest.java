package app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import identity.friend.FriendRequest;
import identity.storage.FriendRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityFriendsCountLookupTest {
    private static final String ANH = "11111111-1111-4111-8111-111111111111";
    private static final String HUY = "33333333-3333-4333-8333-333333333333";

    private MemoryFriends friends;
    private IdentityFriendsCountLookup lookup;

    @BeforeEach
    void setUp() {
        friends = new MemoryFriends();
        lookup = new IdentityFriendsCountLookup(friends);
    }

    @Test
    void countMatchesCurrentFriendships() {
        friends.saveFriendship(ANH, HUY);
        friends.saveFriendship(HUY, ANH);
        assertEquals(1, lookup.currentCount(ANH));
        assertEquals(1, lookup.currentCount(HUY));
    }

    @Test
    void blankOrUnknownUserIsZero() {
        assertEquals(0, lookup.currentCount(null));
        assertEquals(0, lookup.currentCount(" "));
        assertEquals(0, lookup.currentCount(ANH));
    }

    @Test
    void unfriendDropsCount() {
        friends.saveFriendship(ANH, HUY);
        friends.saveFriendship(HUY, ANH);
        friends.deleteFriendship(ANH, HUY);
        friends.deleteFriendship(HUY, ANH);
        assertEquals(0, lookup.currentCount(ANH));
    }

    private static final class MemoryFriends implements FriendRepository {
        private final Map<String, List<String>> byUser = new HashMap<>();

        @Override
        public void saveRequest(FriendRequest request) {
        }

        @Override
        public FriendRequest findRequest(String fromUserId, String toUserId) {
            return null;
        }

        @Override
        public void deleteRequest(String fromUserId, String toUserId) {
        }

        @Override
        public boolean areFriends(String userId, String friendId) {
            return friendIds(userId).contains(friendId);
        }

        @Override
        public void saveFriendship(String userId, String friendId) {
            byUser.computeIfAbsent(userId, key -> new ArrayList<>()).add(friendId);
        }

        @Override
        public void deleteFriendship(String userId, String friendId) {
            List<String> ids = byUser.get(userId);
            if (ids != null) {
                ids.remove(friendId);
            }
        }

        @Override
        public List<String> friendIds(String userId) {
            return List.copyOf(byUser.getOrDefault(userId, List.of()));
        }

        @Override
        public List<FriendRequest> incomingRequests(String userId) {
            return List.of();
        }

        @Override
        public List<FriendRequest> outgoingRequests(String userId) {
            return List.of();
        }
    }
}
