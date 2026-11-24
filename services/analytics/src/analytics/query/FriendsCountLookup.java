package analytics.query;

/**
 * Current friend count for a user (lifetime snapshot). Owned by identity;
 * analytics receives an in-process adapter in the modular monolith.
 */
public interface FriendsCountLookup {
    FriendsCountLookup NONE = userId -> 0L;

    long currentCount(String userId);
}
