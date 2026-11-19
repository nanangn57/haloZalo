package identity.service;

import identity.account.Account;
import org.junit.jupiter.api.Test;
import identity.storage.AccountRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserSearchServiceTest {
    @Test
    void findsByUsernameOrEmail() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account("user-1", "ngoc_anh", "anh@example.com", "hash", Instant.now()));
        UserSearchService search = new UserSearchService(accounts);

        assertEquals("user-1", search.find("ngoc_anh").getUserId());
        assertEquals("user-1", search.find("anh@example.com").getUserId());
    }

    @Test
    void missingUserIsNotFound() {
        UserSearchService search = new UserSearchService(new MemoryAccounts());

        UserSearchService.Rejected rejected = assertThrows(UserSearchService.Rejected.class, () -> search.find("missing"));

        assertEquals(UserSearchService.Rejected.Reason.NOT_FOUND, rejected.getReason());
    }

    @Test
    void blankQueryIsRejected() {
        UserSearchService search = new UserSearchService(new MemoryAccounts());

        UserSearchService.Rejected rejected = assertThrows(UserSearchService.Rejected.class, () -> search.find("  "));

        assertEquals(UserSearchService.Rejected.Reason.VALIDATION, rejected.getReason());
    }

    @Test
    void lookupReturnsKnownIdsInOrderWithoutDuplicates() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account(A, "ngoc_anh", "anh@example.com", "hash", Instant.now()));
        accounts.save(new Account(B, "huy", "huy@example.com", "hash", Instant.now()));
        UserSearchService search = new UserSearchService(accounts);

        List<Account> found = search.lookup(B + ", " + A.toUpperCase() + "," + MISSING + "," + B);

        assertEquals(List.of("huy", "ngoc_anh"), found.stream().map(Account::getUsername).toList());
    }

    @Test
    void lookupRejectsBadIds() {
        UserSearchService search = new UserSearchService(new MemoryAccounts());
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
    }
}
