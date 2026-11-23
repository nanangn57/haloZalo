package identity.access;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.storage.AccountRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformAdminBootstrapTest {
    @Test
    void runPromotesConfiguredUsernames() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account("u1", "ngoc_anh", "a@example.com", "hash", Instant.now(), PlatformRole.USER));
        accounts.save(new Account("u2", "huy", "h@example.com", "hash", Instant.now(), PlatformRole.USER));

        PlatformAdminBootstrap bootstrap = new PlatformAdminBootstrap(accounts, List.of("Ngoc_Anh"));
        bootstrap.run(new DefaultApplicationArguments());

        assertEquals(PlatformRole.ADMIN, accounts.findById("u1").getPlatformRole());
        assertEquals(PlatformRole.USER, accounts.findById("u2").getPlatformRole());
    }

    @Test
    void normalizeSplitsCommaSeparatedNames() {
        assertTrue(PlatformAdminBootstrap.normalize(List.of("a, b", "C")).containsAll(List.of("a", "b", "c")));
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final List<Account> rows = new ArrayList<>();

        @Override
        public void save(Account account) {
            rows.add(account);
        }

        @Override
        public Account findByUsername(String username) {
            return rows.stream()
                .filter(row -> row.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        }

        @Override
        public Account findByUsernameIgnoreCase(String username) {
            return rows.stream()
                .filter(row -> row.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
        }

        @Override
        public Account findByEmail(String email) {
            return null;
        }

        @Override
        public Account findById(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).findFirst().orElse(null);
        }

        @Override
        public void updateBannedAt(String userId, java.time.Instant bannedAt) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withBannedAt(bannedAt));
                    return;
                }
            }
        }

        @Override
        public void updatePasswordHash(String userId, String passwordHash) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withPasswordHash(passwordHash));
                    return;
                }
            }
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
