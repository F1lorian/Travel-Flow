package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.entities.GmailAccount;
import com.sep.sep_backend.eventHandling.EventService;
import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
import com.sep.sep_backend.gmailimport.dto.GmailAutoImportResultDTO;
import com.sep.sep_backend.gmailimport.dto.GmailConnectionDTO;
import com.sep.sep_backend.gmailimport.dto.GmailMessagePreviewDTO;
import com.sep.sep_backend.repos.GmailAccountRepository;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates Gmail import for a user: stores the connection (encrypted refresh token), lists
 * recent messages, and parses a chosen message into an event using the shared mail parser.
 */
@Service
public class GmailImportService {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(GmailImportService.class);

  private static final String GMAIL_READONLY_SCOPE =
      "https://www.googleapis.com/auth/gmail.readonly";

  private final GmailAccountRepository gmailAccountRepository;
  private final GmailOAuthService oAuthService;
  private final GmailClient gmailClient;
  private final TokenCipher tokenCipher;
  private final EventService eventService;
  private final int maxMessages;

  public GmailImportService(
      GmailAccountRepository gmailAccountRepository,
      GmailOAuthService oAuthService,
      GmailClient gmailClient,
      TokenCipher tokenCipher,
      EventService eventService,
      @Value("${gmail.import.max-messages:10}") int maxMessages) {
    this.gmailAccountRepository = gmailAccountRepository;
    this.oAuthService = oAuthService;
    this.gmailClient = gmailClient;
    this.tokenCipher = tokenCipher;
    this.eventService = eventService;
    this.maxMessages = maxMessages;
  }

  /**
   * Completes the OAuth connect: exchanges the code, resolves the Gmail address, and stores (or
   * refreshes) the encrypted refresh token for the user.
   */
  public GmailConnectionDTO connect(long userId, String code) {
    GmailOAuthService.OAuthTokens tokens = oAuthService.exchangeCode(code);
    String email = gmailClient.getProfileEmail(tokens.accessToken());

    GmailAccount account = gmailAccountRepository.findByUserId(userId).orElseGet(GmailAccount::new);
    LocalDateTime now = LocalDateTime.now();
    if (account.getConnectedAt() == null) {
      account.setConnectedAt(now);
    }
    account.setUserId(userId);
    account.setGmailEmail(email);
    account.setEncryptedRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
    account.setScope(GMAIL_READONLY_SCOPE);
    account.setUpdatedAt(now);
    gmailAccountRepository.save(account);

    return new GmailConnectionDTO(true, email);
  }

  /** Returns whether the user has a connected Gmail account and which address it is. */
  public GmailConnectionDTO status(long userId) {
    return gmailAccountRepository
        .findByUserId(userId)
        .map(account -> new GmailConnectionDTO(true, account.getGmailEmail()))
        .orElseGet(() -> new GmailConnectionDTO(false, null));
  }

  /** Lists the most recent messages from the connected mailbox for the import picker. */
  public List<GmailMessagePreviewDTO> listMessages(long userId) {
    String accessToken = accessTokenFor(userId);
    return gmailClient.listRecent(accessToken, maxMessages);
  }

  /**
   * Fetches the selected message as raw RFC822 and parses it into an (unsaved) event using the same
   * pipeline as the .eml and IMAP importers.
   */
  public EventResponseDTO parseMessage(long userId, String messageId) throws Exception {
    if (messageId == null || messageId.isBlank()) {
      throw new IllegalArgumentException("Message id is required");
    }
    String accessToken = accessTokenFor(userId);
    byte[] raw = gmailClient.getRaw(accessToken, messageId);
    return eventService.ParseMailFile(new ByteArrayInputStream(raw));
  }

  /**
   * How many messages are fetched and classified with Gemini in parallel during auto import. Kept
   * low so a full selection does not fire a burst of simultaneous Gemini calls that trips the
   * per-minute quota (HTTP 429); the detector's retry/backoff then spaces out the remaining calls.
   */
  private static final int AUTO_IMPORT_PARALLELISM = 3;

  /** Outcome of processing one message: its event (if any) and whether detection failed. */
  private record ParseOutcome(Optional<EventResponseDTO> event, boolean failed) {}

  /**
   * Parses several messages for the "import automatically" mode and keeps only those that actually
   * contain an event (Gemini-based detection). Non-event mails are skipped; messages whose
   * detection could not run are counted as failed so the client can report them. The messages are
   * fetched and classified with limited parallelism so the batch stays within the request timeout
   * without overwhelming the AI rate limit.
   *
   * @param userId the current user
   * @param messageIds the selected message ids
   * @return the events found plus how many messages could not be checked
   */
  public GmailAutoImportResultDTO autoParse(long userId, List<String> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      throw new IllegalArgumentException("At least one message id is required");
    }

    String accessToken = accessTokenFor(userId);
    List<String> ids =
        messageIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    if (ids.isEmpty()) {
      return new GmailAutoImportResultDTO(List.of(), 0);
    }

    ExecutorService executor =
        Executors.newFixedThreadPool(Math.min(ids.size(), AUTO_IMPORT_PARALLELISM));
    try {
      List<ParseOutcome> outcomes =
          ids.stream()
              .map(
                  id ->
                      CompletableFuture.supplyAsync(() -> parseIfEvent(accessToken, id), executor))
              .toList()
              .stream()
              .map(CompletableFuture::join)
              .toList();

      List<EventResponseDTO> events =
          outcomes.stream().flatMap(outcome -> outcome.event().stream()).toList();
      int failed = (int) outcomes.stream().filter(ParseOutcome::failed).count();
      return new GmailAutoImportResultDTO(events, failed);
    } finally {
      executor.shutdown();
    }
  }

  /**
   * Fetches one message and detects its event. A genuine "no event" yields an empty, non-failed
   * outcome; any error (fetch or AI detection) yields a failed outcome so it can be reported.
   */
  private ParseOutcome parseIfEvent(String accessToken, String messageId) {
    try {
      byte[] raw = gmailClient.getRaw(accessToken, messageId);
      return new ParseOutcome(
          eventService.parseMailFileIfEvent(new ByteArrayInputStream(raw)), false);
    } catch (Exception exception) {
      log.warn("Auto-import: could not process message {}", messageId, exception);
      return new ParseOutcome(Optional.empty(), true);
    }
  }

  /** Removes the stored Gmail connection for the user. */
  @Transactional
  public void disconnect(long userId) {
    gmailAccountRepository.deleteByUserId(userId);
  }

  private String accessTokenFor(long userId) {
    GmailAccount account =
        gmailAccountRepository
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Gmail is not connected"));
    String refreshToken = tokenCipher.decrypt(account.getEncryptedRefreshToken());
    return oAuthService.refreshAccessToken(refreshToken);
  }
}
