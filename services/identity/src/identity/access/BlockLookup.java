package identity.access;

/**
 * In-process lookup so messaging can refuse chat between blocked users without reading identity tables.
 */
public interface BlockLookup {
    /**
     * True when either user has blocked the other.
     */
    boolean isBlocked(String userId, String otherUserId);
}
