package member;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory blocks for tests / no-DB profiles. Not used in profile {@code app}.
 */
@Component
@Profile("!app")
public final class StandaloneBlockChecker implements BlockChecker {
    private final Set<String> pairs = ConcurrentHashMap.newKeySet();

    public void block(String userId, String otherUserId) {
        pairs.add(key(userId, otherUserId));
    }

    @Override
    public boolean isBlocked(String userId, String otherUserId) {
        return pairs.contains(key(userId, otherUserId));
    }

    private static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }
}
