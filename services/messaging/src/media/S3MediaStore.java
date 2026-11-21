package media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores media objects in S3. Download stays on GET /media/{id} so message URLs do not expire.
 */
@Component
@ConditionalOnProperty(name = "messaging.media.backend", havingValue = "s3")
public final class S3MediaStore implements MediaStore {
    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;
    private final String publicBaseUrl;

    public S3MediaStore(
        S3Client s3,
        @Value("${messaging.media.s3.bucket}") String bucket,
        @Value("${messaging.media.s3.key-prefix:media/}") String keyPrefix,
        @Value("${messaging.media.public-base-url}") String publicBaseUrl
    ) {
        this.s3 = s3;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix.endsWith("/") ? keyPrefix : keyPrefix + "/";
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
    }

    @Override
    public Stored store(byte[] bytes, String contentType, String originalName) throws IOException {
        String id = UUID.randomUUID().toString();
        String safeName = originalName == null || originalName.isBlank() ? "bin" : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = keyPrefix + id + "/" + safeName;
        try {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .contentLength((long) bytes.length)
                    .metadata(java.util.Map.of(
                        "media-id", id,
                        "file-name", safeName
                    ))
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        } catch (S3Exception ex) {
            throw new IOException("S3 put failed: " + ex.awsErrorDetails().errorMessage(), ex);
        }
        return new Stored(id, publicBaseUrl + "/media/" + id, contentType, safeName, bytes.length, key);
    }

    @Override
    public Opened open(String mediaId) throws IOException {
        String prefix = keyPrefix + mediaId + "/";
        try {
            var listed = s3.listObjectsV2(b -> b.bucket(bucket).prefix(prefix).maxKeys(1));
            if (listed.contents() == null || listed.contents().isEmpty()) {
                throw new IOException("Media not found");
            }
            String key = listed.contents().get(0).key();
            ResponseInputStream<GetObjectResponse> response = s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build()
            );
            GetObjectResponse meta = response.response();
            String contentType = meta.contentType() == null ? "application/octet-stream" : meta.contentType();
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            long size = meta.contentLength() == null ? -1L : meta.contentLength();
            return new Opened(response, contentType, fileName, size);
        } catch (NoSuchKeyException ex) {
            throw new IOException("Media not found", ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new IOException("Media not found", ex);
            }
            throw new IOException("S3 get failed: " + ex.awsErrorDetails().errorMessage(), ex);
        }
    }

    /** Test helper: wrap bytes as a stream without S3. */
    static Opened fromBytes(byte[] bytes, String contentType, String fileName) {
        return new Opened(new ByteArrayInputStream(bytes), contentType, fileName, bytes.length);
    }
}
