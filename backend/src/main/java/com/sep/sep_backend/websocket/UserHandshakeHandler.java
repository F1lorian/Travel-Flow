package com.sep.sep_backend.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

public class UserHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  protected Principal determineUser(
      org.springframework.http.server.ServerHttpRequest request,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    return (Principal) attributes.get("principal");
  }
}
