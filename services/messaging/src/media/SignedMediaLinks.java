package media;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * HMAC-SHA256 over "mediaId:expires". Anyone holding the link can open the file until it expires, so links are only
 * handed to conversation members, inside messages.
 *
 * expires is rounded up to the next 10 minutes. Every message read within those 10 minutes then gets the same link,
 * so the browser cache keeps working across reloads instead of fetching the image again for each new signature.
 */
public final class SignedMediaLinks implements MediaLinks {
    private static final long BUCKET_SECONDS = 600;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final long ttlSeconds;
    private final Clock clock;

    public SignedMediaLinks(byte[] key, Duration ttl, Clock clock) {
        if (key.length < 32) {
            throw new IllegalArgumentException("Media signing key must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(key, ALGORITHM);
        this.ttlSeconds = ttl.toSeconds();
        this.clock = clock;
    }

    public static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Override
    public String url(String mediaId) {
        long earliest = clock.instant().getEpochSecond() + ttlSeconds;
        long expires = (earliest + BUCKET_SECONDS - 1) / BUCKET_SECONDS * BUCKET_SECONDS;
        return "/media/" + mediaId + "?expires=" + expires + "&sig=" + sign(mediaId, expires);
    }

    @Override
    public boolean verify(String mediaId, long expires, String sig) {
        if (sig == null || expires < clock.instant().getEpochSecond()) {
            return false;
        }
        byte[] expected = sign(mediaId, expires).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(String mediaId, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((mediaId + ":" + expires).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }
}
