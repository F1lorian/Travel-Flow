package com.sep.sep_backend.accountmanagement;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a valid {@code Authorization: Bearer} header into an authenticated {@link
 * SecurityContextHolder} principal.
 *
 * <p>Registered by {@code SecurityConfig} ahead of {@link
 * org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter}. The
 * authentication it sets carries <em>no authorities</em> — the app has no roles, so every protected
 * endpoint only ever asks "is this someone?", and the per-resource rules (trip membership,
 * friendship, privacy) live in the services instead.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  /**
   * Authenticates the request if it carries a valid bearer token, then always continues the chain.
   *
   * <p>A missing, malformed or expired token is deliberately swallowed rather than rejected here:
   * the request simply proceeds unauthenticated and Spring Security's authorization rules turn that
   * into a 401 — or let it through, for the endpoints that are public. Failing fast in this filter
   * would break {@code /api/account/**} and {@code /ws/**}, which must stay reachable without a
   * token.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        String username = jwtService.validateToken(header.substring(7));
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(username, null, List.of());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (IllegalArgumentException e) {
        // invalid or expired token — security chain rejects as 401
      }
    }

    filterChain.doFilter(request, response);
  }
}
