package identity.service;

import identity.account.Account;
import identity.account.Profile;
import identity.auth.UserSummaryList;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public final class UserSearchService {
    private static final int QUERY_MAX = 254;
    public static final int LOOKUP_MAX = 100;

    private final AccountRepository accounts;
    private final ProfileRepository profiles;

    public UserSearchService(AccountRepository accounts, ProfileRepository profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    public Account find(String query) {
        if (query == null || query.isBlank() || query.length() > QUERY_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user query");
        }
        Account account = query.indexOf('@') >= 0 ? accounts.findByEmail(query) : accounts.findByUsername(query);
        if (account == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        return account;
    }

    /**
     * Accounts for comma-separated user ids, in the order given, duplicates removed.
     * Ids without an account are skipped. 1 to 100 ids, each a UUID.
     */
    public List<Account> lookup(String ids) {
        return accounts.findByIds(parseIds(ids));
    }

    /**
     * Lookup with display names for chat headers and conversation lists.
     */
    public List<UserSummaryList.UserSummary> summaries(String ids) {
        List<UserSummaryList.UserSummary> summaries = new ArrayList<>();
        for (Account account : accounts.findByIds(parseIds(ids))) {
            Profile profile = profiles.find(account.getUserId());
            summaries.add(new UserSummaryList.UserSummary(
                account.getUserId(),
                account.getUsername(),
                profile == null ? null : profile.getDisplayName()
            ));
        }
        return summaries;
    }

    private List<String> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "ids is required");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : ids.split(",")) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            unique.add(canonicalUuid(id));
        }
        if (unique.isEmpty() || unique.size() > LOOKUP_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "ids must hold 1 to " + LOOKUP_MAX + " user ids");
        }
        return new ArrayList<>(unique);
    }

    private static String canonicalUuid(String id) {
        try {
            String canonical = UUID.fromString(id).toString();
            if (!canonical.equals(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException();
            }
            return canonical;
        } catch (IllegalArgumentException ex) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid user id");
        }
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
