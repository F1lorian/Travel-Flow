package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.GmailAccount;
import com.sep.sep_backend.eventHandling.EventService;
import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
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
    connectedMailboxWith("m1", "m2");

    EventResponseDTO event = new EventResponseDTO("Concert", 0, null, null);
    when(eventService.prepareMail(any(InputStream.class))).thenAnswer(this::prepareTextMail);
    // m1 has an event, m2 does not.
    when(eventService.detectEventsInBatch(anyList()))
        .thenReturn(
            List.of(
                new EventService.DetectionResult(Optional.of(event), false),
                new EventService.DetectionResult(Optional.empty(), false)));

    GmailAutoImportResultDTO result = service.autoParse(USER_ID, List.of("m1", "m2"));

    assertThat(result.events()).containsExactly(event);
    assertThat(result.failed()).isZero();
    verify(gmailClient).getRaw("access", "m1");
    verify(gmailClient).getRaw("access", "m2");
  }

  @Test
  void autoParseCountsMessagesWhoseDetectionFailed() throws Exception {
    connectedMailboxWith("m1", "m2");

    EventResponseDTO event = new EventResponseDTO("Concert", 0, null, null);
    when(eventService.prepareMail(any(InputStream.class))).thenAnswer(this::prepareTextMail);
    // m1 has an event; m2 could not be checked.
    when(eventService.detectEventsInBatch(anyList()))
        .thenReturn(
            List.of(
                new EventService.DetectionResult(Optional.of(event), false),
                new EventService.DetectionResult(Optional.empty(), true)));

    GmailAutoImportResultDTO result = service.autoParse(USER_ID, List.of("m1", "m2"));

    assertThat(result.events()).containsExactly(event);
    assertThat(result.failed()).isEqualTo(1);
  }

  @Test
  void autoParseCountsMessagesThatCouldNotBeDownloaded() throws Exception {
    connectedMailboxWith("m1", "m2");
    when(gmailClient.getRaw("access", "m2")).thenThrow(new IllegalStateException("gmail down"));

    EventResponseDTO event = new EventResponseDTO("Concert", 0, null, null);
    when(eventService.prepareMail(any(InputStream.class))).thenAnswer(this::prepareTextMail);
    when(eventService.detectEventsInBatch(anyList()))
        .thenReturn(List.of(new EventService.DetectionResult(Optional.of(event), false)));

    GmailAutoImportResultDTO result = service.autoParse(USER_ID, List.of("m1", "m2"));

    // A message we could not even fetch never reaches detection, but must still be reported.
    assertThat(result.events()).containsExactly(event);
    assertThat(result.failed()).isEqualTo(1);
  }

  @Test
  void autoParseSendsAllMailsToGeminiInASingleBatch() throws Exception {
    connectedMailboxWith("m1", "m2", "m3");
    when(eventService.prepareMail(any(InputStream.class))).thenAnswer(this::prepareTextMail);
    when(eventService.detectEventsInBatch(anyList()))
        .thenReturn(
            List.of(
                new EventService.DetectionResult(Optional.empty(), false),
                new EventService.DetectionResult(Optional.empty(), false),
                new EventService.DetectionResult(Optional.empty(), false)));

    service.autoParse(USER_ID, List.of("m1", "m2", "m3"));

    // The whole point: one Gemini round trip for the import, not one per mail. The free tier's cap
    // is counted in requests per day, so per-mail calls drained it in a couple of imports.
    verify(eventService, times(1)).detectEventsInBatch(anyList());
  }

  @Test
  void autoParseSkipsGeminiForMailsThatAlreadyCarryASchemaOrgEvent() throws Exception {
    connectedMailboxWith("m1");

    EventResponseDTO structured = new EventResponseDTO("Flight LH123", 0, null, null);
    when(eventService.prepareMail(any(InputStream.class)))
        .thenReturn(
            new EventService.PreparedMail(
                Optional.of(structured),
                new EventService.MailContent(
                    "Booking", "body", LocalDateTime.of(2026, 5, 20, 12, 0))));

    GmailAutoImportResultDTO result = service.autoParse(USER_ID, List.of("m1"));

    // JSON-LD is authoritative and free — it must never cost a Gemini request.
    assertThat(result.events()).containsExactly(structured);
    assertThat(result.failed()).isZero();
    verify(eventService, never()).detectEventsInBatch(anyList());
  }

  /** Stubs the account lookup, token refresh and a raw body for each of {@code messageIds}. */
  private void connectedMailboxWith(String... messageIds) {
    GmailAccount account = new GmailAccount();
    account.setEncryptedRefreshToken("enc-refresh");
    when(gmailAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("refresh");
    when(oAuthService.refreshAccessToken("refresh")).thenReturn("access");
    for (String id : messageIds) {
      when(gmailClient.getRaw("access", id)).thenReturn(("raw-" + id).getBytes());
    }
  }

  /** A mail with no structured event, so it goes on to the batched Gemini call. */
  private EventService.PreparedMail prepareTextMail(org.mockito.invocation.InvocationOnMock call)
      throws Exception {
    String body = new String(((InputStream) call.getArgument(0)).readAllBytes());
    return new EventService.PreparedMail(
        Optional.empty(),
        new EventService.MailContent(body, body, LocalDateTime.of(2026, 5, 20, 12, 0)));
  }

  @Test
  void autoParseRejectsEmptyMessageList() {
    assertThatThrownBy(() -> service.autoParse(USER_ID, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message id");
  }
}
