package identity.friend;

import java.util.List;

public final class FriendRequestList {
    private final List<FriendRequest> incoming;
    private final List<FriendRequest> outgoing;

    public FriendRequestList(List<FriendRequest> incoming, List<FriendRequest> outgoing) {
        this.incoming = List.copyOf(incoming);
        this.outgoing = List.copyOf(outgoing);
    }

    public List<FriendRequest> getIncoming() {
        return incoming;
    }

    public List<FriendRequest> getOutgoing() {
        return outgoing;
    }
}
