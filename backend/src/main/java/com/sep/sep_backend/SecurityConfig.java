package com.sep.sep_backend;

import com.sep.sep_backend.accountmanagement.JwtAuthFilter;
import com.sep.sep_backend.accountmanagement.JwtService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central Spring Security wiring: stateless JWT authentication for the whole REST API.
 *
 * <p>Sessions are {@link SessionCreationPolicy#STATELESS} and CSRF is disabled, which go together —
 * with no session cookie there is no ambient authority for a cross-site request to abuse, and every
 * call must present its own bearer token. {@link JwtAuthFilter} runs ahead of the username/password
 * filter to translate that token into a principal.
 *
 * <p>Two path groups are open. {@code /api/account/**} has to be, since registering and logging in
 * are how you obtain a token in the first place. {@code /ws/**} is exempt because the STOMP
 * handshake cannot send an {@code Authorization} header; it passes the token as a query parameter
 * and is authenticated separately by {@code JwtHandshakeInterceptor}. Everything else requires a
 * token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtService jwtService;

  public SecurityConfig(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  /**
   * Builds the single filter chain: CORS, no CSRF, no sessions, the public path allowlist, and
   * {@link JwtAuthFilter}.
   *
   * <p>Pre-flight {@code OPTIONS} requests are permitted for every path — browsers send them
   * without the {@code Authorization} header, so rejecting them would make every authenticated
   * cross-origin call fail before it was ever sent.
   *
   * @param http the chain builder supplied by Spring Security
   * @return the configured chain
   * @throws Exception if the builder rejects the configuration
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/account/**")
                    .permitAll()
                    .requestMatchers("/ws/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * The BCrypt encoder used for every hashed secret in the app — passwords, security answers, and
   * the throwaway hashes given to Google accounts that never sign in with a password.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Permissive CORS for local development: any origin, any header, all the usual methods.
   *
   * <p>This is deliberately wide open so the dev server on port 4200 and the container on 8082 can
   * talk to each other without configuration. Credentials are not allowed, so a wildcard origin is
   * not as dangerous as it looks — but a real deployment should still narrow the origin patterns to
   * the actual frontend host.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
