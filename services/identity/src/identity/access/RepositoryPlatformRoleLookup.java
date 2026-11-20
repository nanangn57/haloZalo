package identity.access;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.storage.AccountRepository;

import org.springframework.stereotype.Component;

@Component
public final class RepositoryPlatformRoleLookup implements PlatformRoleLookup {
    private final AccountRepository accounts;

    public RepositoryPlatformRoleLookup(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean isAdmin(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Account account = accounts.findById(userId);
        return account != null && account.getPlatformRole() == PlatformRole.ADMIN;
    }
}
