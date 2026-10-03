package realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Plain WebSocket, no STOMP or SockJS: each frame is exactly the envelope in doc/contract/events.md.
 */
@Configuration
@EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {
    private final RealtimeSessions sessions;
    private final RealtimeProperties properties;

    public RealtimeConfig(RealtimeSessions sessions, RealtimeProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new RealtimeHandler(sessions), "/ws")
            .addInterceptors(new UserHandshakeInterceptor())
            .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Clients never need to send large frames on a push-only socket.
        container.setMaxTextMessageBufferSize((int) properties.maxTextMessageSize().toBytes());
        container.setMaxBinaryMessageBufferSize((int) properties.maxTextMessageSize().toBytes());
        // Backstop only. The heartbeat in RealtimeSessions closes dead sockets much sooner.
        container.setMaxSessionIdleTimeout(properties.pongTimeout().multipliedBy(2).toMillis());
        return container;
    }
}
