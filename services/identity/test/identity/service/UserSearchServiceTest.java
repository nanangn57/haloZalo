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
