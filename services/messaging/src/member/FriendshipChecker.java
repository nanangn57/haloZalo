package member;

/**
 * Whether two users are friends. Owned by identity; in the single app it is backed by identity's FriendshipLookup.
 * Messaging uses it to put a stranger's first message into the recipient's requests.
 */
public interface FriendshipChecker {
    boolean areFriends(String userId, String otherUserId);
}
