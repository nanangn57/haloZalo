package media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "messaging.media.backend", havingValue = "s3")
public class S3ClientConfig {
    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${messaging.media.s3.region:ap-southeast-1}") String region) {
        return S3Client.builder()
            .region(Region.of(region))
            .build();
    }
}
