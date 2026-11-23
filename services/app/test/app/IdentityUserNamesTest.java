package app;

import identity.account.Account;
import identity.account.Profile;
import identity.storage.AccountRepository;
import identity.storage.ProfileRepository;
import identity.service.UserSearchService;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityUserNamesTest {
    private static final String ALICE = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String BOB = "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String CAROL = "cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    @Test
    void displayNameWinsThenUsernameThenSomeone() {
        Map<String, Account> accounts = Map.of(
            ALICE, new Account(ALICE, "alice", "a@x.io", "h", Instant.now()),
            BOB, new Account(BOB, "bob", "b@x.io", "h", Instant.now()));
        Map<String, Profile> profiles = Map.of(ALICE, new Profile(ALICE, "Alice Nguyen", null));
        AccountRepository accountRepo = stub(AccountRepository.class, (method, args) ->
            method.getName().equals("findByIds")
                ? ((List<String>) args[0]).stream().map(accounts::get).filter(java.util.Objects::nonNull).toList()
                : null);
        ProfileRepository profileRepo = stub(ProfileRepository.class, (method, args) -> profiles.get((String) args[0]));
        IdentityUserNames names = new IdentityUserNames(new UserSearchService(accountRepo, profileRepo));

        assertEquals("Alice Nguyen", names.displayNameOf(ALICE));
        assertEquals("bob", names.displayNameOf(BOB));
        assertEquals("Someone", names.displayNameOf(CAROL));
        assertEquals("Someone", names.displayNameOf(null));
    }

    private interface Answer {
        Object of(java.lang.reflect.Method method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answer.of(method, args));
    }
}
