package identity.access;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.storage.AccountRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryPlatformRoleLookupTest {
    @Test
    void isAdminFollowsStoredPlatformRole() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account("u1", "user", "u@example.com", "hash", Instant.now(), PlatformRole.USER));
        accounts.save(new Account("a1", "boss", "a@example.com", "hash", Instant.now(), PlatformRole.ADMIN));
        RepositoryPlatformRoleLookup lookup = new RepositoryPlatformRoleLookup(accounts);

        assertFalse(lookup.isAdmin("u1"));
        assertTrue(lookup.isAdmin("a1"));
        assertFalse(lookup.isAdmin("missing"));
        assertFalse(lookup.isAdmin(null));
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final List<Account> rows = new ArrayList<>();

        @Override
        public void save(Account account) {
            rows.add(account);
        }

        @Override
        public Account findByUsername(String username) {
            return rows.stream().filter(row -> row.getUsername().equals(username)).findFirst().orElse(null);
        }

        @Override
        public Account findByEmail(String email) {
            return rows.stream().filter(row -> row.getEmail().equals(email)).findFirst().orElse(null);
        }

        @Override
        public Account findById(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).findFirst().orElse(null);
        }

        @Override
        public void updatePlatformRole(String userId, PlatformRole role) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withPlatformRole(role));
                    return;
                }
            }
        }
    }
}
