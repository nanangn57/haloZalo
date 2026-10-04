package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan("realtime")
@SpringBootApplication(scanBasePackages = {
    "api", "app", "conversation", "event", "http", "message", "reaction", "realtime", "service", "storage"
})
public class MessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
