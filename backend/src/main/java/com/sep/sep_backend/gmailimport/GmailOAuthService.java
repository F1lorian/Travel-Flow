package com.sep.sep_backend.gmailimport;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Handles the Google OAuth 2.0 authorization-code exchange and refresh for Gmail import. Uses the
 * {@code google-api-client} library already on the classpath.
 */
@Service
public class GmailOAuthService {

  /** Access token (short-lived) and refresh token (long-lived) from an authorization-code grant. */
  public record OAuthTokens(String accessToken, String refreshToken) {}

  private final NetHttpTransport transport = new NetHttpTransport();
  private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  public GmailOAuthService(
      @Value("${google.oauth.client-id:}") String clientId,
      @Value("${google.oauth.client-secret:}") String clientSecret,
      @Value("${google.oauth.redirect-uri:}") String redirectUri) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  /**
   * Exchanges the authorization code the frontend received for access and refresh tokens.
   *
   * @param code one-time authorization code from the Google consent redirect
   * @return the access token plus the refresh token to persist
   */
  public OAuthTokens exchangeCode(String code) {
    requireConfigured();
    if (!StringUtils.hasText(code)) {
      throw new IllegalArgumentException("Authorization code is required");
    }

    GoogleTokenResponse response;
    try {
      response =
          new GoogleAuthorizationCodeTokenRequest(
                  transport, jsonFactory, clientId, clientSecret, code, redirectUri)
              .execute();
    } catch (Exception exception) {
      throw new IllegalArgumentException("Could not exchange Google authorization code");
    }

    String refreshToken = response.getRefreshToken();
    if (!StringUtils.hasText(refreshToken)) {
      // Google only returns a refresh token when access was freshly consented; the frontend must
      // request access_type=offline and prompt=consent so this always holds on connect.
      throw new IllegalArgumentException(
          "Google did not return a refresh token; please re-connect and grant access");
    }
    return new OAuthTokens(response.getAccessToken(), refreshToken);
  }

  /**
   * Refreshes a short-lived access token from a stored refresh token.
   *
   * @param refreshToken the decrypted refresh token
   * @return a fresh access token
   */
  public String refreshAccessToken(String refreshToken) {
    requireConfigured();
    if (!StringUtils.hasText(refreshToken)) {
      throw new IllegalArgumentException("Refresh token is required");
    }
    try {
      GoogleTokenResponse response =
          new GoogleRefreshTokenRequest(
                  transport, jsonFactory, refreshToken, clientId, clientSecret)
              .execute();
      return response.getAccessToken();
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Could not refresh Gmail access; the connection may have been revoked");
    }
  }

  private void requireConfigured() {
    if (!StringUtils.hasText(clientId)
        || !StringUtils.hasText(clientSecret)
        || !StringUtils.hasText(redirectUri)) {
      throw new IllegalStateException("Gmail import is not configured");
    }
  }
}
