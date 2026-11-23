package app;

import identity.access.BlockLookup;
import member.BlockChecker;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-process adapter: messaging BlockChecker → identity BlockLookup. No cross-schema JDBC.
 */
@Component
@Primary
@Profile("app")
public final class IdentityBlockChecker implements BlockChecker {
    private final BlockLookup blocks;

    public IdentityBlockChecker(BlockLookup blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean isBlocked(String userId, String otherUserId) {
        return blocks.isBlocked(userId, otherUserId);
    }
}
