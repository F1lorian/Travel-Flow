package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.entities.GmailAccount;
import com.sep.sep_backend.gmailimport.GoogleOAuthClient.GoogleTokenResponse;
import com.sep.sep_backend.repos.GmailAccountRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the Gmail OAuth lifecycle for a user: building the consent URL, exchanging the authorization
 * code for tokens, storing them encrypted, and transparently refreshing the access token when it
 * has expired.
 */
@Service
public class GmailOAuthService {

  /** Refresh a little before the real expiry so an in-flight request never uses a stale token. */
  private static final long EXPIRY_SKEW_SECONDS = 60;

  private final GoogleOAuthClient oAuthClient;
  private final GmailApiClient gmailApiClient;
  private final GmailAccountRepository gmailAccountRepository;
  private final TokenCipher tokenCipher;

  public GmailOAuthService(
      GoogleOAuthClient oAuthClient,
      GmailApiClient gmailApiClient,
      GmailAccountRepository gmailAccountRepository,
      TokenCipher tokenCipher) {
    this.oAuthClient = oAuthClient;
    this.gmailApiClient = gmailApiClient;
    this.gmailAccountRepository = gmailAccountRepository;
    this.tokenCipher = tokenCipher;
  }

  /**
   * Builds the Google consent URL the user must visit to grant read-only Gmail access.
   *
   * <p>{@code access_type=offline} together with {@code prompt=consent} guarantees Google returns a
   * refresh token, which is what lets the backend keep reading mail after the access token expires.
   *
   * @param state opaque value echoed back by Google, used to protect against CSRF
   * @return the fully built authorization URL
   */
  public String buildAuthorizationUrl(String state) {
    return GoogleOAuthClient.AUTH_ENDPOINT
        + "?client_id="
        + encode(oAuthClient.getClientId())
        + "&redirect_uri="
        + encode(oAuthClient.getRedirectUri())
        + "&response_type=code"
        + "&scope="
        + encode(GoogleOAuthClient.GMAIL_READONLY_SCOPE)
        + "&access_type=offline"
        + "&include_granted_scopes=true"
        + "&prompt=consent"
        + "&state="
        + encode(state);
  }

  /**
   * Exchanges the authorization code for tokens and stores an encrypted Gmail connection for the
   * user. If the user already had a connection, its tokens are replaced.
   *
   * @param userId local user id
   * @param code authorization code returned by Google
   * @return the connected Gmail address
   */
  @Transactional
  public String connectWithCode(long userId, String code) {
    if (!StringUtils.hasText(code)) {
      throw new GmailAuthException("Missing Google authorization code");
    }
    GoogleTokenResponse tokens = oAuthClient.exchangeAuthorizationCode(code);
    if (!StringUtils.hasText(tokens.refreshToken())) {
      // Without a refresh token we cannot keep the connection alive; force a fresh consent.
      throw new GmailAuthException(
          "Google did not return a refresh token; please grant offline access again");
    }

    String emailAddress = gmailApiClient.getProfileEmailAddress(tokens.accessToken());

    GmailAccount account =
        gmailAccountRepository.findByUserId(userId).orElseGet(() -> new GmailAccount(userId));
    account.setEmailAddress(emailAddress);
    account.setRefreshTokenEncrypted(tokenCipher.encrypt(tokens.refreshToken()));
    applyAccessToken(account, tokens);
    gmailAccountRepository.save(account);
    return emailAddress;
  }

  /**
   * Returns a valid (non-expired) access token for the user, refreshing it first if necessary.
   *
   * @param userId local user id
   * @return a usable access token
   * @throws GmailAuthException if the user has not connected Gmail or the token cannot be refreshed
   */
  @Transactional
  public String getValidAccessToken(long userId) {
    GmailAccount account =
        gmailAccountRepository
            .findByUserId(userId)
            .orElseThrow(() -> new GmailAuthException("Gmail account is not connected"));

    if (!isAccessTokenExpired(account)) {
      return tokenCipher.decrypt(account.getAccessTokenEncrypted());
    }

    String refreshToken = tokenCipher.decrypt(account.getRefreshTokenEncrypted());
    if (!StringUtils.hasText(refreshToken)) {
      throw new GmailAuthException("Stored Gmail refresh token is missing; please reconnect Gmail");
    }
    GoogleTokenResponse tokens = oAuthClient.refreshAccessToken(refreshToken);
    applyAccessToken(account, tokens);
    gmailAccountRepository.save(account);
    return tokens.accessToken();
  }

  /**
   * Reports whether the user has a usable Gmail connection.
   *
   * @param userId local user id
   * @return {@code true} if connected
   */
  public boolean isConnected(long userId) {
    return gmailAccountRepository.existsByUserId(userId);
  }

  /**
   * Returns the connected Gmail address, if any.
   *
   * @param userId local user id
   * @return the connected email address, or empty when not connected
   */
  public Optional<String> getConnectedEmail(long userId) {
    return gmailAccountRepository.findByUserId(userId).map(GmailAccount::getEmailAddress);
  }

  /**
   * Removes the user's Gmail connection and its stored tokens.
   *
   * @param userId local user id
   */
  @Transactional
  public void disconnect(long userId) {
    gmailAccountRepository.deleteByUserId(userId);
  }

  private void applyAccessToken(GmailAccount account, GoogleTokenResponse tokens) {
    account.setAccessTokenEncrypted(tokenCipher.encrypt(tokens.accessToken()));
    account.setAccessTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()));
  }

  private boolean isAccessTokenExpired(GmailAccount account) {
    if (account.getAccessTokenEncrypted() == null || account.getAccessTokenExpiresAt() == null) {
      return true;
    }
    return Instant.now()
        .isAfter(account.getAccessTokenExpiresAt().minusSeconds(EXPIRY_SKEW_SECONDS));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
