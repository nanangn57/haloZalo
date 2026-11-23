package identity.storage;

import identity.account.Account;
import identity.account.PlatformRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public interface AccountRepository {
    void save(Account account);

    Account findByUsername(String username);

    /**
     * Same as {@link #findByUsername(String)} but case-insensitive. Used for admin bootstrap.
     */
    default Account findByUsernameIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Account exact = findByUsername(username);
        if (exact != null) {
            return exact;
        }
        return findByUsername(username.toLowerCase());
    }

    Account findByEmail(String email);

    Account findById(String userId);

    void updatePlatformRole(String userId, PlatformRole role);

    /**
     * Sets or clears the ban mark. Pass {@code null} to lift the ban.
     */
    void updateBannedAt(String userId, Instant bannedAt);

    void updatePasswordHash(String userId, String passwordHash);

    /**
     * Accounts for these ids, in the order given. Ids without an account are left out.
     */
    default List<Account> findByIds(List<String> userIds) {
        List<Account> found = new ArrayList<>();
        for (String userId : userIds) {
            Account account = findById(userId);
            if (account != null) {
                found.add(account);
            }
        }
        return found;
    }
}
