package media;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where the bytes live. The key is the mediaId. LocalMediaStore keeps them on disk; an S3 store can replace it
 * without changing the API, since clients only ever see signed links.
 */
public interface MediaStore {
    /**
     * Stores the stream under key and returns the number of bytes written. Stops and stores nothing once more than
     * maxBytes arrive, so a client cannot fill the disk by lying about the size.
     */
    long put(String key, InputStream content, long maxBytes) throws IOException, TooLarge;

    /**
     * The stored bytes, or null when nothing is stored under key.
     */
    Resource get(String key);

    void delete(String key);

    final class TooLarge extends Exception {
        public TooLarge() {
            super(null, null, false, false);
        }
    }
}
