package media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LocalMediaStoreTest {
    @TempDir
    Path temp;

    @Test
    void storeAndOpenRoundTrip() throws Exception {
        LocalMediaStore store = new LocalMediaStore(temp.toString(), "http://localhost:8080");
        byte[] bytes = new byte[] {1, 2, 3, 4};
        MediaStore.Stored stored = store.store(bytes, "image/png", "dot.png");
        assertTrue(stored.url().endsWith("/media/" + stored.id()));
        assertEquals("dot.png", stored.fileName());
        try (MediaStore.Opened opened = store.open(stored.id())) {
            assertEquals("image/png", opened.contentType());
            assertArrayEquals(bytes, opened.stream().readAllBytes());
        }
    }

    @Test
    void openKeepsUploadedVideoContentTypeEvenWithoutProbeableExtension() throws Exception {
        LocalMediaStore store = new LocalMediaStore(temp.toString(), "http://localhost:8080");
        MediaStore.Stored stored = store.store(new byte[] {9, 9, 9}, "video/mp4", "clip.bin");
        try (MediaStore.Opened opened = store.open(stored.id())) {
            assertEquals("video/mp4", opened.contentType());
        }
    }

    @Test
    void openMissingThrows() throws Exception {
        LocalMediaStore store = new LocalMediaStore(temp.toString(), "http://localhost:8080");
        assertThrows(Exception.class, () -> store.open("00000000-0000-4000-8000-000000000001"));
        assertTrue(Files.isDirectory(temp));
    }
}
