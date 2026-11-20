package media;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedMediaLinksTest {
    private static final String ID = "dddddddd-0000-4000-8000-000000000001";
    private static final byte[] KEY = new byte[32];

    @Test
    void linkVerifiesUntilItExpiresAndNotAfter() {
        Instant start = Instant.parse("2026-11-20T10:03:00Z");
        String url = links(start).url(ID);
        long expires = Long.parseLong(url.replaceAll(".*expires=(\\d+).*", "$1"));
        String sig = url.replaceAll(".*sig=", "");

        assertTrue(links(start).verify(ID, expires, sig));
        assertTrue(links(Instant.ofEpochSecond(expires)).verify(ID, expires, sig));
        assertFalse(links(Instant.ofEpochSecond(expires + 1)).verify(ID, expires, sig));
    }

    @Test
    void expiryIsRoundedUpSoReadsMinutesApartGetTheSameLinkForTheCache() {
        Instant start = Instant.parse("2026-11-20T10:01:00Z");
        String first = links(start).url(ID);

        assertEquals(first, links(start.plusSeconds(500)).url(ID));
        // At least the TTL, at most TTL plus one 10-minute bucket.
        long expires = Long.parseLong(first.replaceAll(".*expires=(\\d+).*", "$1"));
        assertTrue(expires >= start.plus(Duration.ofHours(1)).getEpochSecond());
        assertTrue(expires <= start.plus(Duration.ofHours(1)).plusSeconds(600).getEpochSecond());
        assertEquals(0, expires % 600);
    }

    @Test
    void signatureIsBoundToIdExpiryAndKey() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        String url = links(now).url(ID);
        long expires = Long.parseLong(url.replaceAll(".*expires=(\\d+).*", "$1"));
        String sig = url.replaceAll(".*sig=", "");

        assertFalse(links(now).verify("dddddddd-0000-4000-8000-000000000002", expires, sig));
        assertFalse(links(now).verify(ID, expires + 600, sig));
        assertFalse(links(now).verify(ID, expires, null));
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        assertFalse(new SignedMediaLinks(otherKey, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC)).verify(ID, expires, sig));
    }

    @Test
    void shortKeysAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new SignedMediaLinks(new byte[16], Duration.ofHours(1), Clock.systemUTC()));
    }

    private static SignedMediaLinks links(Instant now) {
        return new SignedMediaLinks(KEY, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC));
    }
}
