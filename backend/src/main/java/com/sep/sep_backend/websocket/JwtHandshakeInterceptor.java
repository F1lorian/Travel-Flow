package com.sep.sep_backend.websocket;

import com.sep.sep_backend.accountmanagement.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Validates the JWT query parameter before allowing a STOMP WebSocket handshake. */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtService jwtService;

  public JwtHandshakeInterceptor(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  /** Stores a Principal in the handshake attributes when the token is valid. */
  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    if (request instanceof ServletServerHttpRequest servletRequest) {
      HttpServletRequest servlet = servletRequest.getServletRequest();
      String token = servlet.getParameter("token");

      if (token != null) {
        try {
          String username = jwtService.validateToken(token);
          attributes.put("principal", (Principal) () -> username);
          return true;
        } catch (IllegalArgumentException e) {
          // invalid or expired token -> handshake abgelehnt
        }
      }
    }
    return false;
  }

  /**
   * No-op hook after the handshake; authentication is handled before the connection is accepted.
   */
  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
