package identity.access;

/**
 * In-process lookup so messaging can tell friends from strangers without reading identity tables.
 */
public interface FriendshipLookup {
    boolean areFriends(String userId, String otherUserId);
}
