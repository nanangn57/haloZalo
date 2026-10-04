package realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Settings for /ws. See resources/application.yml for what each one protects against.
 */
@ConfigurationProperties("messaging.realtime")
public record RealtimeProperties(
    @DefaultValue("redis") Bus bus,
    @DefaultValue("*") List<String> allowedOrigins,
    @DefaultValue("25s") Duration pingInterval,
    @DefaultValue("60s") Duration pongTimeout,
    @DefaultValue("10s") Duration sendTimeLimit,
    @DefaultValue("512KB") DataSize sendBufferLimit,
    @DefaultValue("8KB") DataSize maxTextMessageSize) {

    public enum Bus {
        REDIS,
        LOCAL
    }
}
