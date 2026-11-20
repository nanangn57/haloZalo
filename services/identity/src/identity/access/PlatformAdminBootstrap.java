package identity.access;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.storage.AccountRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Promotes matching usernames to platform ADMIN on startup from config.
 */
@Component
public final class PlatformAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final AccountRepository accounts;
    private final Set<String> adminUsernames;

    public PlatformAdminBootstrap(
        AccountRepository accounts,
        @Value("${identity.platform-admins:}") List<String> platformAdmins
    ) {
        this.accounts = accounts;
        this.adminUsernames = normalize(platformAdmins);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUsernames.isEmpty()) {
            return;
        }
        for (String username : adminUsernames) {
            Account account = accounts.findByUsernameIgnoreCase(username);
            if (account == null) {
                continue;
            }
            if (account.getPlatformRole() == PlatformRole.ADMIN) {
                continue;
            }
            accounts.updatePlatformRole(account.getUserId(), PlatformRole.ADMIN);
            log.info("Promoted platform admin: {}", username);
        }
    }

    public static Set<String> normalize(List<String> usernames) {
        Set<String> normalized = new LinkedHashSet<>();
        if (usernames == null) {
            return normalized;
        }
        for (String raw : usernames) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split(",")) {
                String trimmed = part.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }
}
