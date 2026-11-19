package identity.service;

import identity.account.Account;
import identity.storage.AccountRepository;

import org.springframework.stereotype.Service;

@Service
public final class UserSearchService {
    private static final int QUERY_MAX = 254;

    private final AccountRepository accounts;

    public UserSearchService(AccountRepository accounts) {
        this.accounts = accounts;
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
