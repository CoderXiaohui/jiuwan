package com.jiuwan.config;

import com.jiuwan.websocket.RoomSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final RoomSocketHandler handler;
  private final String[] origins;

  public WebSocketConfig(
      RoomSocketHandler handler, @Value("${jiuwan.allowed-origins}") String[] origins) {
    this.handler = handler;
    this.origins = origins;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws").setAllowedOrigins(origins);
  }
}
