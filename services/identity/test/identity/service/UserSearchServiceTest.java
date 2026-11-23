package identity.service;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.account.Profile;
import org.junit.jupiter.api.Test;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserSearchServiceTest {
    @Test
    void findsByUsernameOrEmail() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account("user-1", "ngoc_anh", "anh@example.com", "hash", Instant.now()));
        UserSearchService search = new UserSearchService(accounts, new MemoryProfiles());

        assertEquals("user-1", search.find("ngoc_anh").getUserId());
        assertEquals("user-1", search.find("anh@example.com").getUserId());
    }

    @Test
    void missingUserIsNotFound() {
        UserSearchService search = new UserSearchService(new MemoryAccounts(), new MemoryProfiles());

        UserSearchService.Rejected rejected = assertThrows(UserSearchService.Rejected.class, () -> search.find("missing"));

        assertEquals(UserSearchService.Rejected.Reason.NOT_FOUND, rejected.getReason());
    }

    @Test
    void blankQueryIsRejected() {
        UserSearchService search = new UserSearchService(new MemoryAccounts(), new MemoryProfiles());

        UserSearchService.Rejected rejected = assertThrows(UserSearchService.Rejected.class, () -> search.find("  "));

        assertEquals(UserSearchService.Rejected.Reason.VALIDATION, rejected.getReason());
    }

    @Test
    void lookupReturnsKnownIdsInOrderWithoutDuplicates() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account(A, "ngoc_anh", "anh@example.com", "hash", Instant.now()));
        accounts.save(new Account(B, "huy", "huy@example.com", "hash", Instant.now()));
        UserSearchService search = new UserSearchService(accounts, new MemoryProfiles());

        List<Account> found = search.lookup(B + ", " + A.toUpperCase() + "," + MISSING + "," + B);

        assertEquals(List.of("huy", "ngoc_anh"), found.stream().map(Account::getUsername).toList());
    }

    @Test
    void lookupRejectsBadIds() {
        UserSearchService search = new UserSearchService(new MemoryAccounts(), new MemoryProfiles());
        String tooMany = String.join(",", java.util.stream.IntStream.range(0, 101)
            .mapToObj(i -> String.format("00000000-0000-4000-8000-%012d", i)).toList());

        for (String ids : new String[] {null, " ", ",", "not-a-uuid", "1-1-1-1-1", tooMany}) {
            UserSearchService.Rejected rejected = assertThrows(UserSearchService.Rejected.class, () -> search.lookup(ids));
            assertEquals(UserSearchService.Rejected.Reason.VALIDATION, rejected.getReason(), String.valueOf(ids));
        }
    }

    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String B = "33333333-3333-4333-8333-333333333333";
    private static final String MISSING = "99999999-9999-4999-8999-999999999999";

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

    private static final class MemoryProfiles implements ProfileRepository {
        private final Map<String, Profile> rows = new HashMap<>();

        @Override
        public Profile find(String userId) {
            return rows.get(userId);
        }

        @Override
        public void save(Profile profile) {
            rows.put(profile.getUserId(), profile);
        }
    }
}
