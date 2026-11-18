package identity.friend;

public final class FriendRequest {
    private final String fromUserId;
    private final String toUserId;

    public FriendRequest(String fromUserId, String toUserId) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }
}
