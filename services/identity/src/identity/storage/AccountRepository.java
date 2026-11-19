package identity.storage;

import identity.account.Account;

import java.util.ArrayList;
import java.util.List;

public interface AccountRepository {
    void save(Account account);

    Account findByUsername(String username);

    Account findByEmail(String email);

    Account findById(String userId);

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
