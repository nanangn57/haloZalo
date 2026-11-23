package app;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Modular monolith: identity + messaging + analytics in one process (profile {@code app}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "realtime")
@ComponentScan(
    basePackages = {
        "app",
        "identity",
        "analytics",
        "api",
        "conversation",
        "event",
        "http",
        "media",
        "member",
        "message",
        "presence",
        "push",
        "reaction",
        "realtime",
        "service",
        "storage"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*\\.MessagingApplication|.*\\.IdentityApplication"
    )
)
public class HaloZaloApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HaloZaloApplication.class);
        application.setAdditionalProfiles("app");
        application.run(args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
