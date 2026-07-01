package com.sep.sep_backend.gmailimport;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client for Google's OAuth 2.0 token endpoint. Handles the two grant types the Gmail
 * import feature needs: exchanging an authorization code for tokens, and refreshing an expired
 * access token with a stored refresh token.
 *
 * <p>Using a plain {@link RestClient} keeps the feature dependency-light (no Google client library)
 * and makes the network boundary trivial to mock in tests.
 */
@Component
public class GoogleOAuthClient {

  private static final String DEFAULT_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

  /** Read-only Gmail scope: lets the app read messages but never modify or send mail. */
  public static final String GMAIL_READONLY_SCOPE =
      "https://www.googleapis.com/auth/gmail.readonly";

  /** Google authorization endpoint the user is redirected to for consent. */
  public static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  public GoogleOAuthClient(
      RestClient.Builder restClientBuilder,
      @Value("${google.oauth.client-id:}") String clientId,
      @Value("${google.oauth.client-secret:}") String clientSecret,
      @Value("${google.oauth.redirect-uri:}") String redirectUri,
      @Value("${google.oauth.token-endpoint:" + DEFAULT_TOKEN_ENDPOINT + "}")
          String tokenEndpoint) {
    this.restClient = restClientBuilder.baseUrl(tokenEndpoint).build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  /** Token values returned by Google. The refresh token is only present on the first exchange. */
  public record GoogleTokenResponse(
      String accessToken, String refreshToken, long expiresInSeconds, String scope) {}

  /**
   * Exchanges a one-time authorization code for an access token and (on first consent) a refresh
   * token.
   *
   * @param code authorization code received from Google
   * @return the issued tokens
   * @throws GmailAuthException if the exchange fails
   */
  public GoogleTokenResponse exchangeAuthorizationCode(String code) {
    requireConfigured();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("redirect_uri", redirectUri);
    form.add("grant_type", "authorization_code");
    return postForTokens(form, "Could not exchange the Google authorization code");
  }

  /**
   * Uses a refresh token to obtain a fresh access token.
   *
   * @param refreshToken previously stored refresh token
   * @return the new access token (Google does not return a new refresh token here)
   * @throws GmailAuthException if the refresh fails (for example a revoked token)
   */
  public GoogleTokenResponse refreshAccessToken(String refreshToken) {
    requireConfigured();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("refresh_token", refreshToken);
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("grant_type", "refresh_token");
    return postForTokens(form, "Could not refresh the Gmail access token");
  }

  private GoogleTokenResponse postForTokens(
      MultiValueMap<String, String> form, String errorMessage) {
    try {
      JsonNode node =
          restClient
              .post()
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(JsonNode.class);
      if (node == null || !node.hasNonNull("access_token")) {
        throw new GmailAuthException(errorMessage + ": no access token in response");
      }
      return new GoogleTokenResponse(
          node.path("access_token").asText(),
          node.path("refresh_token").asText(null),
          node.path("expires_in").asLong(3600),
          node.path("scope").asText(null));
    } catch (RestClientResponseException exception) {
      throw new GmailAuthException(errorMessage + ": " + exception.getStatusText(), exception);
    }
  }

  private void requireConfigured() {
    if (!StringUtils.hasText(clientId)
        || !StringUtils.hasText(clientSecret)
        || !StringUtils.hasText(redirectUri)) {
      throw new GmailAuthException(
          "Gmail import is not configured: set google.oauth.client-id, "
              + "google.oauth.client-secret and google.oauth.redirect-uri");
    }
  }

  public String getClientId() {
    return clientId;
  }

  public String getRedirectUri() {
    return redirectUri;
  }
}
