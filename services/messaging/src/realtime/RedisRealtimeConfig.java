package realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Every instance publishes frames to one Redis channel and every instance subscribes, so a socket on any
 * instance gets the frame. Redis pub/sub does not store anything: a frame nobody receives is gone, and the
 * client gets it back through catch-up by seq.
 */
@Configuration
@ConditionalOnProperty(name = "messaging.realtime.bus", havingValue = "redis", matchIfMissing = true)
public class RedisRealtimeConfig {
    static final String CHANNEL = "messaging.realtime";

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeConfig.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * While Redis is unreachable, Lettuce would otherwise queue commands and block the HTTP request that
     * stored the message. Fail at once instead: the message is stored, and clients catch up by seq.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .build());
    }

    @Bean
    public RealtimeBus redisRealtimeBus(StringRedisTemplate redis) {
        return frame -> {
            try {
                redis.convertAndSend(CHANNEL, JSON.writeValueAsString(frame));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Could not write realtime frame", ex);
            }
        };
    }

    @Bean
    public RedisMessageListenerContainer realtimeListener(RedisConnectionFactory connections, RealtimeSessions sessions) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener(new FrameListener(sessions), new ChannelTopic(CHANNEL));
        return container;
    }

    static final class FrameListener implements MessageListener {
        private final RealtimeSessions sessions;

        FrameListener(RealtimeSessions sessions) {
            this.sessions = sessions;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                RealtimeFrame frame = JSON.readValue(new String(message.getBody(), StandardCharsets.UTF_8), RealtimeFrame.class);
                sessions.deliver(frame.recipients(), frame.envelope());
            } catch (Exception ex) {
                log.warn("Dropped a realtime frame that could not be read", ex);
            }
        }
    }
}
