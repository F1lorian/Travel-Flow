package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.GmailAccount;
import com.sep.sep_backend.eventHandling.EventService;
import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
import com.sep.sep_backend.exception.EventDetectionException;
import com.sep.sep_backend.gmailimport.dto.GmailAutoImportResultDTO;
import com.sep.sep_backend.gmailimport.dto.GmailConnectionDTO;
import com.sep.sep_backend.gmailimport.dto.GmailMessagePreviewDTO;
import com.sep.sep_backend.repos.GmailAccountRepository;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GmailImportServiceTest {

  private static final long USER_ID = 42L;

  @Mock private GmailAccountRepository gmailAccountRepository;
  @Mock private GmailOAuthService oAuthService;
  @Mock private GmailClient gmailClient;
  @Mock private TokenCipher tokenCipher;
  @Mock private EventService eventService;

  private GmailImportService service;

  @BeforeEach
  void setUp() {
    service =
        new GmailImportService(
            gmailAccountRepository, oAuthService, gmailClient, tokenCipher, eventService, 10);
  }

  @Test
  void connectStoresEncryptedRefreshTokenAndReturnsEmail() {
    when(oAuthService.exchangeCode("auth-code"))
        .thenReturn(new GmailOAuthService.OAuthTokens("access", "refresh"));
    when(gmailClient.getProfileEmail("access")).thenReturn("user@gmail.com");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(tokenCipher.encrypt("refresh")).thenReturn("enc-refresh");

    GmailConnectionDTO result = service.connect(USER_ID, "auth-code");

    assertThat(result.connected()).isTrue();
    assertThat(result.email()).isEqualTo("user@gmail.com");

    ArgumentCaptor<GmailAccount> captor = ArgumentCaptor.forClass(GmailAccount.class);
    verify(gmailAccountRepository).save(captor.capture());
    GmailAccount saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getGmailEmail()).isEqualTo("user@gmail.com");
    assertThat(saved.getEncryptedRefreshToken()).isEqualTo("enc-refresh");
    assertThat(saved.getConnectedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void connectKeepsOriginalConnectedAtWhenReconnecting() {
    LocalDateTime firstConnect = LocalDateTime.now().minusDays(3);
    GmailAccount existing = new GmailAccount();
    existing.setUserId(USER_ID);
    existing.setConnectedAt(firstConnect);

    when(oAuthService.exchangeCode("auth-code"))
        .thenReturn(new GmailOAuthService.OAuthTokens("access", "refresh"));
    when(gmailClient.getProfileEmail("access")).thenReturn("user@gmail.com");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(tokenCipher.encrypt("refresh")).thenReturn("enc-refresh");

    service.connect(USER_ID, "auth-code");

    ArgumentCaptor<GmailAccount> captor = ArgumentCaptor.forClass(GmailAccount.class);
    verify(gmailAccountRepository).save(captor.capture());
    assertThat(captor.getValue().getConnectedAt()).isEqualTo(firstConnect);
  }

  @Test
  void statusReportsConnectedAccount() {
    GmailAccount account = new GmailAccount();
    account.setGmailEmail("user@gmail.com");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

    GmailConnectionDTO status = service.status(USER_ID);

    assertThat(status.connected()).isTrue();
    assertThat(status.email()).isEqualTo("user@gmail.com");
  }

  @Test
  void statusReportsDisconnectedWhenNoAccount() {
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    GmailConnectionDTO status = service.status(USER_ID);

    assertThat(status.connected()).isFalse();
    assertThat(status.email()).isNull();
  }

  @Test
  void listMessagesRefreshesTokenAndReturnsPreviews() {
    GmailAccount account = new GmailAccount();
    account.setEncryptedRefreshToken("enc-refresh");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("refresh");
    when(oAuthService.refreshAccessToken("refresh")).thenReturn("access");
    GmailMessagePreviewDTO preview =
        new GmailMessagePreviewDTO("m1", "Concert", "sender@x.com", "today", "snippet");
    when(gmailClient.listRecent("access", 10)).thenReturn(List.of(preview));

    List<GmailMessagePreviewDTO> messages = service.listMessages(USER_ID);

    assertThat(messages).containsExactly(preview);
  }

  @Test
  void parseMessageFetchesRawAndDelegatesToMailParser() throws Exception {
    GmailAccount account = new GmailAccount();
    account.setEncryptedRefreshToken("enc-refresh");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("refresh");
    when(oAuthService.refreshAccessToken("refresh")).thenReturn("access");
    when(gmailClient.getRaw("access", "m1")).thenReturn("raw-bytes".getBytes());
    EventResponseDTO parsed = new EventResponseDTO("Concert", 0, null, null);
    when(eventService.ParseMailFile(any(InputStream.class))).thenReturn(parsed);

    EventResponseDTO result = service.parseMessage(USER_ID, "m1");

    assertThat(result).isSameAs(parsed);
    verify(eventService).ParseMailFile(any(InputStream.class));
  }

  @Test
  void listMessagesFailsWhenNotConnected() {
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listMessages(USER_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not connected");

    verify(gmailClient, never()).listRecent(any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void parseMessageRejectsBlankMessageId() {
    assertThatThrownBy(() -> service.parseMessage(USER_ID, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Message id");
  }

  @Test
  void autoParseKeepsOnlyMessagesThatContainAnEvent() throws Exception {
    GmailAccount account = new GmailAccount();
    account.setEncryptedRefreshToken("enc-refresh");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("refresh");
    when(oAuthService.refreshAccessToken("refresh")).thenReturn("access");
    when(gmailClient.getRaw("access", "m1")).thenReturn("raw-1".getBytes());
    when(gmailClient.getRaw("access", "m2")).thenReturn("raw-2".getBytes());

    EventResponseDTO event = new EventResponseDTO("Concert", 0, null, null);
    // m1 (raw-1) has an event, m2 (raw-2) does not. Keyed on content so the parallel, unordered
    // execution stays deterministic.
    when(eventService.parseMailFileIfEvent(any(InputStream.class)))
        .thenAnswer(
            invocation -> {
              InputStream in = invocation.getArgument(0);
              String content = new String(in.readAllBytes());
              return content.equals("raw-1") ? Optional.of(event) : Optional.empty();
            });

    GmailAutoImportResultDTO result = service.autoParse(USER_ID, List.of("m1", "m2"));

    assertThat(result.events()).containsExactly(event);
    assertThat(result.failed()).isZero();
    verify(gmailClient).getRaw("access", "m1");
    verify(gmailClient).getRaw("access", "m2");
  }

  @Test
  void autoParseCountsMessagesWhoseDetectionFailed() throws Exception {
    GmailAccount account = new GmailAccount();
    account.setEncryptedRefreshToken("enc-refresh");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("refresh");
    when(oAuthService.refreshAccessToken("refresh")).thenReturn("access");
    when(gmailClient.getRaw("access", "m1")).thenReturn("raw-1".getBytes());
    when(gmailClient.getRaw("access", "m2")).thenReturn("raw-2".getBytes());

    EventResponseDTO event = new EventResponseDTO("Concert", 0, null, null);
    // m1 has an event; m2's detection fails (e.g. Gemini error / rate limit).
    when(eventService.parseMailFileIfEvent(any(InputStream.class)))
        .thenAnswer(
            invocation -> {
              InputStream in = invocation.getArgument(0);
              String content = new String(in.readAllBytes());
              if (content.equals("raw-1")) {
                return Optional.of(event);
              }
              throw new EventDetectionException("detection failed");
            });

    GmailAutoImportResultDTO result = service.autoParse(USER_ID, List.of("m1", "m2"));

    assertThat(result.events()).containsExactly(event);
    assertThat(result.failed()).isEqualTo(1);
  }

  @Test
  void autoParseRejectsEmptyMessageList() {
    assertThatThrownBy(() -> service.autoParse(USER_ID, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message id");
  }
}
