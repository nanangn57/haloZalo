package media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

@Configuration
public class MediaConfig {
    private static final Logger log = LoggerFactory.getLogger(MediaConfig.class);

    @Bean
    public MediaStore mediaStore(MediaProperties properties) throws IOException {
        return new LocalMediaStore(Path.of(properties.storageDir()));
    }

    @Bean
    public MediaLinks mediaLinks(MediaProperties properties) {
        byte[] key;
        if (properties.signingKey().isBlank()) {
            log.warn("MESSAGING_MEDIA_SIGNING_KEY is not set: media links use a random key and stop working on restart");
            key = SignedMediaLinks.randomKey();
        } else {
            key = properties.signingKey().getBytes(StandardCharsets.UTF_8);
        }
        return new SignedMediaLinks(key, properties.linkTtl(), Clock.systemUTC());
    }
}
