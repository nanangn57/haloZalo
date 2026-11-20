package media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Files on local disk, under directory/ab/abcd...: the first two characters of the key spread files over 256
 * folders. Keys are canonical UUIDs, so they never contain path separators. Each file is written to a temporary
 * name first and moved into place, so a reader never sees half a file.
 */
public final class LocalMediaStore implements MediaStore {
    private static final Logger log = LoggerFactory.getLogger(LocalMediaStore.class);

    private final Path root;

    public LocalMediaStore(Path root) throws IOException {
        this.root = Files.createDirectories(root).toAbsolutePath().normalize();
    }

    @Override
    public long put(String key, InputStream content, long maxBytes) throws IOException, TooLarge {
        Path target = path(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), key, ".part");
        try {
            long written = 0;
            byte[] buffer = new byte[64 * 1024];
            try (OutputStream out = Files.newOutputStream(temporary)) {
                int read;
                while ((read = content.read(buffer)) != -1) {
                    written += read;
                    if (written > maxBytes) {
                        throw new TooLarge();
                    }
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return written;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Resource get(String key) {
        Path file = path(key);
        return Files.isRegularFile(file) ? new FileSystemResource(file) : null;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException ex) {
            log.warn("Could not delete media file {}", key, ex);
        }
    }

    private Path path(String key) {
        Path file = root.resolve(key.substring(0, 2)).resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Bad media key");
        }
        return file;
    }
}
