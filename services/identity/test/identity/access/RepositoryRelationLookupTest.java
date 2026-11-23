package identity.access;

import identity.friend.FriendRequest;
import identity.storage.BlockRepository;
import identity.storage.FriendRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryRelationLookupTest {
    @Test
    void friendshipHoldsInEitherDirectionAndNeverForBlankIds() {
        MemoryFriends friends = new MemoryFriends();
        friends.saveFriendship("a", "b");
        RepositoryFriendshipLookup lookup = new RepositoryFriendshipLookup(friends);

        assertTrue(lookup.areFriends("a", "b"));
        assertTrue(lookup.areFriends("b", "a"));
        assertFalse(lookup.areFriends("a", "c"));
        assertFalse(lookup.areFriends(null, "a"));
        assertFalse(lookup.areFriends("a", " "));
    }

    @Test
    void blockHoldsWhicheverUserBlockedAndNeverForBlankIds() {
        MemoryBlocks blocks = new MemoryBlocks();
        blocks.save("a", "b");
        RepositoryBlockLookup lookup = new RepositoryBlockLookup(blocks);

        assertTrue(lookup.isBlocked("a", "b"));
        assertTrue(lookup.isBlocked("b", "a"));
        assertFalse(lookup.isBlocked("a", "c"));
        assertFalse(lookup.isBlocked("", "a"));
        assertFalse(lookup.isBlocked("a", null));
    }

    private static final class MemoryFriends implements FriendRepository {
        private final Set<String> pairs = new HashSet<>();

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
            return pairs.contains(userId + ">" + friendId);
        }

        @Override
        public void saveFriendship(String userId, String friendId) {
            pairs.add(userId + ">" + friendId);
        }

        @Override
        public void deleteFriendship(String userId, String friendId) {
            pairs.remove(userId + ">" + friendId);
        }

        @Override
        public List<String> friendIds(String userId) {
            return List.of();
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

    private static final class MemoryBlocks implements BlockRepository {
        private final Set<String> pairs = new HashSet<>();

        @Override
        public void save(String blockerId, String blockedId) {
            pairs.add(blockerId + ">" + blockedId);
        }

        @Override
        public void delete(String blockerId, String blockedId) {
            pairs.remove(blockerId + ">" + blockedId);
        }

        @Override
        public boolean exists(String blockerId, String blockedId) {
            return pairs.contains(blockerId + ">" + blockedId);
        }

        @Override
        public List<String> blockedIds(String blockerId) {
            return List.of();
        }
    }
}
