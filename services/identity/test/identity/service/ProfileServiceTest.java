package identity.service;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.account.Profile;
import identity.account.ProfilePatch;
import identity.account.ProfileView;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileServiceTest {
    private MemoryAccounts accounts;
    private MemoryProfiles profiles;
    private ProfileService service;
    private String userId;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        profiles = new MemoryProfiles();
        service = new ProfileService(accounts, profiles);
        userId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        accounts.save(new Account(userId, "ngoc", "ngoc@example.com", "hash", Instant.now()));
    }

    @Test
    void viewStartsWithoutDisplayName() {
        ProfileView view = service.view(userId);
        assertEquals("ngoc", view.getUsername());
        assertNull(view.getDisplayName());
        assertNull(view.getAvatar());
    }

    @Test
    void updateKeepsOmittedFieldsAndClearsBlankAvatar() throws Exception {
        ObjectMapper json = new ObjectMapper();
        service.update(userId, ProfilePatch.from(json.readTree("{\"displayName\":\"Ngoc Anh\",\"avatar\":\"https://cdn/a.png\"}")));
        ProfileView renamed = service.update(userId, ProfilePatch.from(json.readTree("{\"displayName\":\"Anh\"}")));
        assertEquals("Anh", renamed.getDisplayName());
        assertEquals("https://cdn/a.png", renamed.getAvatar());

        ProfileView cleared = service.update(userId, ProfilePatch.from(json.readTree("{\"avatar\":\"\"}")));
        assertEquals("Anh", cleared.getDisplayName());
        assertNull(cleared.getAvatar());
    }

    @Test
    void blankNameAndUnknownUserFail() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ProfileService.Rejected blank = assertThrows(
            ProfileService.Rejected.class,
            () -> service.update(userId, ProfilePatch.from(json.readTree("{\"displayName\":\" \"}")))
        );
        assertEquals(ProfileService.Rejected.Reason.VALIDATION, blank.getReason());

        ProfileService.Rejected missing = assertThrows(
            ProfileService.Rejected.class,
            () -> service.view("missing")
        );
        assertEquals(ProfileService.Rejected.Reason.NOT_FOUND, missing.getReason());
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final List<Account> rows = new ArrayList<>();

        @Override
        public void save(Account account) {
            rows.add(account);
        }

        @Override
        public Account findByUsername(String username) {
            return null;
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
        private Profile row;

        @Override
        public Profile find(String userId) {
            return row != null && row.getUserId().equals(userId) ? row : null;
        }

        @Override
        public void save(Profile profile) {
            row = profile;
        }
    }
}
