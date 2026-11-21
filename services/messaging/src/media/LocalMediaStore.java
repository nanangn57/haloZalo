package media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stores uploaded bytes under a local directory and returns a public URL path under /media/{id}.
 */
@Component
@ConditionalOnProperty(name = "messaging.media.backend", havingValue = "local", matchIfMissing = true)
public final class LocalMediaStore implements MediaStore {
    private final Path root;
    private final String publicBaseUrl;

    public LocalMediaStore(
        @Value("${messaging.media.storage-dir:./data/media}") String storageDir,
        @Value("${messaging.media.public-base-url:http://localhost:8082}") String publicBaseUrl
    ) throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
    }

    @Override
    public Stored store(byte[] bytes, String contentType, String originalName) throws IOException {
        String id = UUID.randomUUID().toString();
        String safeName = originalName == null || originalName.isBlank() ? "bin" : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path file = root.resolve(id + "_" + safeName);
        Files.write(file, bytes);
        return new Stored(id, publicBaseUrl + "/media/" + id, contentType, safeName, bytes.length, file.getFileName().toString());
    }

    @Override
    public Opened open(String mediaId) throws IOException {
        Path path = resolve(mediaId);
        String probe = Files.probeContentType(path);
        String contentType = probe == null ? "application/octet-stream" : probe;
        String fileName = path.getFileName().toString();
        long size = Files.size(path);
        InputStream stream = Files.newInputStream(path);
        return new Opened(stream, contentType, fileName, size);
    }

    Path resolve(String mediaId) throws IOException {
        try (var stream = Files.list(root)) {
            return stream
                .filter(path -> path.getFileName().toString().startsWith(mediaId + "_"))
                .findFirst()
                .orElseThrow(() -> new IOException("Media not found"));
        }
    }
}
