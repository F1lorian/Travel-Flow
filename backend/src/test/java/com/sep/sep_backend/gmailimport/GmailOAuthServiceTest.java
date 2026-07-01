package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.GmailAccount;
import com.sep.sep_backend.gmailimport.GoogleOAuthClient.GoogleTokenResponse;
import com.sep.sep_backend.repos.GmailAccountRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests for Gmail OAuth token storage and automatic refresh. */
@ExtendWith(MockitoExtension.class)
class GmailOAuthServiceTest {

  @Mock private GoogleOAuthClient oAuthClient;
  @Mock private GmailApiClient gmailApiClient;
  @Mock private GmailAccountRepository repository;

  private final TokenCipher tokenCipher = new TokenCipher("unit-test-secret");
  private GmailOAuthService service;

  @BeforeEach
  void setUp() {
    service = new GmailOAuthService(oAuthClient, gmailApiClient, repository, tokenCipher);
  }

  @Test
  void buildAuthorizationUrlRequestsOfflineReadonlyAccess() {
    when(oAuthClient.getClientId()).thenReturn("client-123");
    when(oAuthClient.getRedirectUri()).thenReturn("http://localhost:4200/gmail-import");

    String url = service.buildAuthorizationUrl("state-xyz");

    assertThat(url).startsWith(GoogleOAuthClient.AUTH_ENDPOINT);
    assertThat(url).contains("client_id=client-123");
    assertThat(url).contains("access_type=offline");
    assertThat(url).contains("prompt=consent");
    assertThat(url).contains("gmail.readonly");
    assertThat(url).contains("state=state-xyz");
  }

  @Test
  void connectWithCodeStoresEncryptedTokens() {
    when(oAuthClient.exchangeAuthorizationCode("auth-code"))
        .thenReturn(new GoogleTokenResponse("access-1", "refresh-1", 3600, "scope"));
    when(gmailApiClient.getProfileEmailAddress("access-1")).thenReturn("user@gmail.com");
    when(repository.findByUserId(7L)).thenReturn(Optional.empty());

    String email = service.connectWithCode(7L, "auth-code");

    assertThat(email).isEqualTo("user@gmail.com");
    ArgumentCaptor<GmailAccount> captor = ArgumentCaptor.forClass(GmailAccount.class);
    verify(repository).save(captor.capture());
    GmailAccount saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(7L);
    assertThat(saved.getEmailAddress()).isEqualTo("user@gmail.com");
    // Tokens must be stored encrypted, not in plaintext.
    assertThat(saved.getRefreshTokenEncrypted()).isNotEqualTo("refresh-1");
    assertThat(tokenCipher.decrypt(saved.getRefreshTokenEncrypted())).isEqualTo("refresh-1");
    assertThat(tokenCipher.decrypt(saved.getAccessTokenEncrypted())).isEqualTo("access-1");
  }

  @Test
  void connectWithCodeFailsWhenNoRefreshTokenReturned() {
    when(oAuthClient.exchangeAuthorizationCode("auth-code"))
        .thenReturn(new GoogleTokenResponse("access-1", null, 3600, "scope"));

    assertThatThrownBy(() -> service.connectWithCode(7L, "auth-code"))
        .isInstanceOf(GmailAuthException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void getValidAccessTokenReturnsStoredTokenWhenNotExpired() {
    GmailAccount account = new GmailAccount(7L);
    account.setAccessTokenEncrypted(tokenCipher.encrypt("still-valid"));
    account.setAccessTokenExpiresAt(Instant.now().plusSeconds(600));
    when(repository.findByUserId(7L)).thenReturn(Optional.of(account));

    String token = service.getValidAccessToken(7L);

    assertThat(token).isEqualTo("still-valid");
    verify(oAuthClient, never()).refreshAccessToken(any());
  }

  @Test
  void getValidAccessTokenRefreshesWhenExpired() {
    GmailAccount account = new GmailAccount(7L);
    account.setAccessTokenEncrypted(tokenCipher.encrypt("expired"));
    account.setRefreshTokenEncrypted(tokenCipher.encrypt("refresh-1"));
    account.setAccessTokenExpiresAt(Instant.now().minusSeconds(10));
    when(repository.findByUserId(7L)).thenReturn(Optional.of(account));
    when(oAuthClient.refreshAccessToken("refresh-1"))
        .thenReturn(new GoogleTokenResponse("fresh-token", null, 3600, "scope"));

    String token = service.getValidAccessToken(7L);

    assertThat(token).isEqualTo("fresh-token");
    verify(oAuthClient).refreshAccessToken("refresh-1");
    verify(repository).save(account);
  }

  @Test
  void getValidAccessTokenThrowsWhenNotConnected() {
    when(repository.findByUserId(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getValidAccessToken(7L))
        .isInstanceOf(GmailAuthException.class)
        .hasMessageContaining("not connected");
    verify(oAuthClient, never()).refreshAccessToken(eq("any"));
  }
}
