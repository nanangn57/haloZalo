package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan({"realtime", "media"})
@SpringBootApplication(scanBasePackages = {
    "api", "app", "conversation", "event", "http", "media", "member", "message", "reaction", "realtime", "service", "storage",
    "analytics.record", "analytics.store"
})
public class MessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
