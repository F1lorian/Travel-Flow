package com.sep.sep_backend.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/** Uses the Principal prepared by JwtHandshakeInterceptor as the WebSocket user. */
public class UserHandshakeHandler extends DefaultHandshakeHandler {

  /** Returns the authenticated Principal stored during the handshake. */
  @Override
  protected Principal determineUser(
      org.springframework.http.server.ServerHttpRequest request,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    return (Principal) attributes.get("principal");
  }
}
