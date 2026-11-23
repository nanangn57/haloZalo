package identity.service;

import identity.account.Account;
import identity.account.Profile;
import identity.account.ProfilePatch;
import identity.account.ProfileView;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;

import org.springframework.stereotype.Service;

@Service
public final class ProfileService {
    private static final int NAME_MAX = 64;
    private static final int AVATAR_MAX = 2048;
    private static final int BIO_MAX = 280;

    private final AccountRepository accounts;
    private final ProfileRepository profiles;

    public ProfileService(AccountRepository accounts, ProfileRepository profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    public ProfileView view(String userId) {
        Account account = requireAccount(userId);
        Profile profile = profiles.find(account.getUserId());
        return view(account, profile);
    }

    public ProfileView update(String userId, ProfilePatch patch) {
        Account account = requireAccount(userId);
        if (patch == null || (!patch.hasDisplayName() && !patch.hasAvatar() && !patch.hasBio())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Nothing to update");
        }
        Profile current = profiles.find(account.getUserId());
        String displayName = current == null ? null : current.getDisplayName();
        String avatar = current == null ? null : current.getAvatar();
        String bio = current == null ? null : current.getBio();
        if (patch.hasDisplayName()) {
            displayName = name(patch.getDisplayName());
        }
        if (patch.hasAvatar()) {
            avatar = avatar(patch.getAvatar());
        }
        if (patch.hasBio()) {
            bio = bio(patch.getBio());
        }
        Profile saved = new Profile(account.getUserId(), displayName, avatar, bio);
        profiles.save(saved);
        return view(account, saved);
    }

    private Account requireAccount(String userId) {
        Account account = userId == null ? null : accounts.findById(userId);
        if (account == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        return account;
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.length() > NAME_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid display name");
        }
        return value;
    }

    private static String avatar(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > AVATAR_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid avatar");
        }
        return value;
    }

    private static String bio(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > BIO_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid bio");
        }
        return value;
    }

    private static ProfileView view(Account account, Profile profile) {
        return new ProfileView(
            account.getUserId(),
            account.getUsername(),
            account.getEmail(),
            account.getPlatformRole(),
            profile == null ? null : profile.getDisplayName(),
            profile == null ? null : profile.getAvatar(),
            profile == null ? null : profile.getBio()
        );
    }

    public static final class Rejected extends RuntimeException {
        public enum Reason {
            VALIDATION,
            NOT_FOUND
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
