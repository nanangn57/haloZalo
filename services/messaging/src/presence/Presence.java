package presence;

import java.time.Instant;

/**
 * Whether a user has a socket open now, and when this instance last saw one open or close.
 * lastSeenAt is null for a user never seen since the process started.
 */
public record Presence(String userId, boolean online, Instant lastSeenAt) {
}
