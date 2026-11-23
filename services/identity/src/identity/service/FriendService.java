package identity.service;

import identity.account.Account;
import identity.account.Profile;
import identity.friend.FriendRequest;
import identity.friend.FriendRequestList;
import identity.friend.FriendView;
import identity.storage.AccountRepository;
import identity.storage.BlockRepository;
import identity.storage.FriendRepository;
import identity.storage.ProfileRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import identity.event.EventPublisher;
import identity.storage.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public final class FriendService {
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final FriendRepository friends;
    private final BlockRepository blocks;
    private final EventPublisher events;

    public FriendService(
        AccountRepository accounts,
        ProfileRepository profiles,
        FriendRepository friends,
        BlockRepository blocks,
        EventPublisher events
    ) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.friends = friends;
        this.blocks = blocks;
        this.events = events;
    }

    public FriendRequest send(String callerId, String targetUserId) {
        String caller = canonical(callerId);
        String target = canonical(targetUserId);
        if (!valid(caller) || !valid(target)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
        if (caller.equals(target)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Cannot friend yourself");
        }
        if (accounts.findById(target) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        if (areBlocked(caller, target)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Cannot send a friend request to this user");
        }
        if (friends.areFriends(caller, target) || friends.findRequest(caller, target) != null || friends.findRequest(target, caller) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Friend request already exists");
        }
        FriendRequest request = new FriendRequest(caller, target);
        try {
            friends.saveRequest(request);
        } catch (DuplicateKeyException ex) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Friend request already exists");
        }
        events.publishFriendRequestSent(caller, target);
        return request;
    }

    public FriendView accept(String callerId, String fromUserId) {
        String caller = canonical(callerId);
        String from = canonical(fromUserId);
        if (!valid(caller) || !valid(from)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
        if (friends.findRequest(from, caller) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Friend request not found");
        }
        friends.deleteRequest(from, caller);
        friends.saveFriendship(caller, from);
        friends.saveFriendship(from, caller);
        events.publishFriendRequestAccepted(from, caller);
        return view(from);
    }

    public void reject(String callerId, String fromUserId) {
        String caller = canonical(callerId);
        String from = canonical(fromUserId);
        requireValid(caller, from);
        if (friends.findRequest(from, caller) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Friend request not found");
        }
        friends.deleteRequest(from, caller);
    }

    public void cancel(String callerId, String toUserId) {
        String caller = canonical(callerId);
        String to = canonical(toUserId);
        requireValid(caller, to);
        if (friends.findRequest(caller, to) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Friend request not found");
        }
        friends.deleteRequest(caller, to);
    }

    public FriendRequestList requests(String callerId) {
        String caller = canonical(callerId);
        if (!valid(caller)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
        return new FriendRequestList(friends.incomingRequests(caller), friends.outgoingRequests(caller));
    }

    public void unfriend(String callerId, String friendUserId) {
        String caller = canonical(callerId);
        String friend = canonical(friendUserId);
        requireValid(caller, friend);
        if (!friends.areFriends(caller, friend)) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Friendship not found");
        }
        friends.deleteFriendship(caller, friend);
        friends.deleteFriendship(friend, caller);
    }

    /** Blocks the user. Any friendship or pending request between the two is dropped. Blocking twice is fine. */
    public FriendView block(String callerId, String targetUserId) {
        String caller = canonical(callerId);
        String target = canonical(targetUserId);
        requireValid(caller, target);
        if (caller.equals(target)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Cannot block yourself");
        }
        if (accounts.findById(target) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        blocks.save(caller, target);
        friends.deleteRequest(caller, target);
        friends.deleteRequest(target, caller);
        friends.deleteFriendship(caller, target);
        friends.deleteFriendship(target, caller);
        return view(target);
    }

    public void unblock(String callerId, String targetUserId) {
        String caller = canonical(callerId);
        String target = canonical(targetUserId);
        requireValid(caller, target);
        if (!blocks.exists(caller, target)) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Block not found");
        }
        blocks.delete(caller, target);
    }

    public List<FriendView> listBlocked(String callerId) {
        String caller = canonical(callerId);
        if (!valid(caller)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
        List<FriendView> views = new ArrayList<>();
        for (String blockedId : blocks.blockedIds(caller)) {
            views.add(view(blockedId));
        }
        return views;
    }

    /** True when either user has blocked the other. */
    public boolean areBlocked(String userId, String otherUserId) {
        String user = canonical(userId);
        String other = canonical(otherUserId);
        return blocks.exists(user, other) || blocks.exists(other, user);
    }

    public List<FriendView> list(String callerId) {
        String caller = canonical(callerId);
        if (!valid(caller)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
        List<FriendView> views = new ArrayList<>();
        for (String friendId : friends.friendIds(caller)) {
            views.add(view(friendId));
        }
        return views;
    }

    private static void requireValid(String first, String second) {
        if (!valid(first) || !valid(second)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
    }

    private FriendView view(String userId) {
        Account account = accounts.findById(userId);
        if (account == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        Profile profile = profiles.find(userId);
        return new FriendView(account.getUserId(), account.getUsername(), profile == null ? null : profile.getDisplayName());
    }

    private static String canonical(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private static boolean valid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static final class Rejected extends RuntimeException {
        public enum Reason {
            VALIDATION,
            NOT_FOUND,
            CONFLICT,
            FORBIDDEN
        }

        private final Reason reason;

        public Rejected(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
