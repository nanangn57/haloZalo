package media;

import java.time.Clock;
import java.time.Duration;

/**
 * Real signing with a fixed key, so tests can check links without wiring Spring.
 */
public final class TestLinks {
    public static final MediaLinks LINKS = new SignedMediaLinks(new byte[32], Duration.ofHours(1), Clock.systemUTC());

    private TestLinks() {
    }
}
