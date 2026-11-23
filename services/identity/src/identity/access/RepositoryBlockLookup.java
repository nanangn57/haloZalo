package identity.access;

import identity.storage.BlockRepository;

import org.springframework.stereotype.Component;

@Component
public final class RepositoryBlockLookup implements BlockLookup {
    private final BlockRepository blocks;

    public RepositoryBlockLookup(BlockRepository blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean isBlocked(String userId, String otherUserId) {
        if (blank(userId) || blank(otherUserId)) {
            return false;
        }
        return blocks.exists(userId, otherUserId) || blocks.exists(otherUserId, userId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
