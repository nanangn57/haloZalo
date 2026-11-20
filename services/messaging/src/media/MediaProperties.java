package media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Settings for media upload and download. See resources/application.yml.
 *
 * @param directory      where LocalMediaStore keeps the files
 * @param signingKey     HMAC key for download links. Blank means a random key per process: links then stop working
 *                       on restart, which is fine on one machine but not with two instances behind a load balancer
 * @param linkTtl        how long a download link stays valid at least
 * @param maxImageSize   largest IMAGE upload
 * @param maxVideoSize   largest VIDEO upload. spring.servlet.multipart.max-file-size must be at least the largest limit
 * @param maxDocumentSize largest DOCUMENT upload
 */
@ConfigurationProperties("messaging.media")
public record MediaProperties(
    @DefaultValue("./data/media") String directory,
    @DefaultValue("") String signingKey,
    @DefaultValue("1h") Duration linkTtl,
    @DefaultValue("10MB") DataSize maxImageSize,
    @DefaultValue("25MB") DataSize maxVideoSize,
    @DefaultValue("10MB") DataSize maxDocumentSize) {
}
