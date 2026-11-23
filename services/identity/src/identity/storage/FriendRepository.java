package identity.storage;

import identity.friend.FriendRequest;

public interface FriendRepository {
    void saveRequest(FriendRequest request);

    FriendRequest findRequest(String fromUserId, String toUserId);

    void deleteRequest(String fromUserId, String toUserId);

    boolean areFriends(String userId, String friendId);

    void saveFriendship(String userId, String friendId);

    void deleteFriendship(String userId, String friendId);

    java.util.List<String> friendIds(String userId);

    /** Requests addressed to this user. */
    java.util.List<FriendRequest> incomingRequests(String userId);

    /** Requests this user has sent. */
    java.util.List<FriendRequest> outgoingRequests(String userId);
}
