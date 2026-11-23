package member;

/**
 * Whether either user has blocked the other. Owned by identity; in the single app it is backed by identity's BlockLookup.
 */
public interface BlockChecker {
    boolean isBlocked(String userId, String otherUserId);
}
