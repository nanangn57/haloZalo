package media;

import java.io.IOException;
import java.io.InputStream;

/**
 * Binary media storage. Implementations persist bytes and open them again by media id.
 */
public interface MediaStore {
    Stored store(byte[] bytes, String contentType, String originalName) throws IOException;

    /**
     * Opens stored bytes for download. Caller must close the stream.
     */
    Opened open(String mediaId) throws IOException;

    record Stored(String id, String url, String contentType, String fileName, long size, String storedName) {
    }

    record Opened(InputStream stream, String contentType, String fileName, long size) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}
