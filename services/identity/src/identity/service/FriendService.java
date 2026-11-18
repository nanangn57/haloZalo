package identity.service;

import identity.account.Account;
import identity.account.Profile;
import identity.friend.FriendRequest;
import identity.friend.FriendView;
import identity.storage.AccountRepository;
import identity.storage.FriendRepository;
import identity.storage.ProfileRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import identity.storage.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public final class FriendService {
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final FriendRepository friends;

    public FriendService(AccountRepository accounts, ProfileRepository profiles, FriendRepository friends) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.friends = friends;
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
        if (friends.areFriends(caller, target) || friends.findRequest(caller, target) != null || friends.findRequest(target, caller) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Friend request already exists");
        }
        FriendRequest request = new FriendRequest(caller, target);
        try {
            friends.saveRequest(request);
        } catch (DuplicateKeyException ex) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Friend request already exists");
        }
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
        return view(from);
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
            CONFLICT
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
