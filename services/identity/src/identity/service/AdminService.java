package identity.service;

import identity.account.Account;
import identity.storage.AccountRepository;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Platform-admin actions on other accounts. The caller must hold platformRole ADMIN.
 */
@Service
public final class AdminService {
    private final AccountRepository accounts;
    private final Clock clock;

    public AdminService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    public void requireAdmin(String callerId) {
        Account caller = callerId == null ? null : accounts.findById(callerId);
        if (caller == null) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        if (!caller.isPlatformAdmin()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.FORBIDDEN, "Platform admin only");
        }
    }

    public void ban(String callerId, String targetUserId) {
        Account target = requireTarget(callerId, targetUserId);
        if (target.getUserId().equals(callerId)) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Cannot ban yourself");
        }
        if (target.isBanned()) {
            return;
        }
        accounts.updateBannedAt(target.getUserId(), clock.instant());
    }

    public void unban(String callerId, String targetUserId) {
        Account target = requireTarget(callerId, targetUserId);
        if (!target.isBanned()) {
            return;
        }
        accounts.updateBannedAt(target.getUserId(), null);
    }

    private Account requireTarget(String callerId, String targetUserId) {
        Account caller = callerId == null ? null : accounts.findById(callerId);
        if (caller == null) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        if (!caller.isPlatformAdmin()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.FORBIDDEN, "Platform admin only");
        }
        if (!validUuid(targetUserId)) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.VALIDATION, "Invalid user id");
        }
        Account target = accounts.findById(targetUserId.toLowerCase());
        if (target == null) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.NOT_FOUND, "User not found");
        }
        return target;
    }

    private static boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
