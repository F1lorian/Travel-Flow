package com.sep.sep_backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/** Configures the STOMP WebSocket endpoint and in-memory broker destinations. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

  public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
    this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
  }

  /** Registers the JWT-protected WebSocket endpoint used by the Angular client. */
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins("http://localhost:4200", "http://localhost:4201")
        .addInterceptors(jwtHandshakeInterceptor)
        .setHandshakeHandler(new UserHandshakeHandler());
  }

  /** Enables topic, queue, application, and per-user destination prefixes. */
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }
}
