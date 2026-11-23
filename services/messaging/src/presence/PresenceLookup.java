package presence;

import java.util.Collection;
import java.util.List;

public interface PresenceLookup {
    /**
     * One entry per given id, in the order given. A user never seen is offline with no lastSeenAt.
     */
    List<Presence> of(Collection<String> userIds);
}
