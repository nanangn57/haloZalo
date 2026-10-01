package storage;

import account.Account;

public interface AccountRepository {
    void save(Account account);

    Account findByUsername(String username);

    Account findByEmail(String email);

    Account findById(String userId);
}
