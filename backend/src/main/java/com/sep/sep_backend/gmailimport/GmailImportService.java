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
import java.util.ArrayList;
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
   * How many messages are downloaded from Gmail at once. This bounds Gmail traffic only — the
   * classification that follows is a single batched Gemini request, not one per message, so this no
   * longer has anything to do with the AI rate limit.
   */
  private static final int AUTO_IMPORT_PARALLELISM = 3;

  /** A downloaded message: its id, and either its parsed content or the failure to report. */
  private record FetchedMail(String messageId, EventService.PreparedMail prepared) {}

  /**
   * Parses several messages for the "import automatically" mode and keeps only those that actually
   * contain an event. Non-event mails are skipped; messages that could not be checked are counted
   * as failed so the client can report them.
   *
   * <p>The work is deliberately split in two. Downloading and reading the mails is local and cheap,
   * and any mail carrying a schema.org event is finished right there without touching the AI. Only
   * what is left goes to Gemini — and it goes in <em>one batched request</em> rather than one
   * request per mail. That matters because the free tier's cap counts requests per day (20 for
   * {@code gemini-2.5-flash}), so per-mail calls burned the entire daily budget in two imports and
   * left later ones failing for no visible reason.
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

    // A message we could not even download is already a failure; it never reaches detection.
    List<FetchedMail> fetched = fetchAll(accessToken, ids);
    int failed = ids.size() - fetched.size();

    List<EventResponseDTO> events = new ArrayList<>();
    List<FetchedMail> needAi = new ArrayList<>();
    for (FetchedMail mail : fetched) {
      Optional<EventResponseDTO> jsonLd = mail.prepared().jsonLdEvent();
      if (jsonLd.isPresent()) {
        jsonLd.ifPresent(events::add);
      } else {
        needAi.add(mail);
      }
    }

    if (!needAi.isEmpty()) {
      List<EventService.DetectionResult> results =
          eventService.detectEventsInBatch(
              needAi.stream().map(mail -> mail.prepared().content()).toList());

      for (EventService.DetectionResult result : results) {
        if (result.failed()) {
          failed++;
        } else {
          result.event().ifPresent(events::add);
        }
      }
    }

    return new GmailAutoImportResultDTO(List.copyOf(events), failed);
  }

  /**
   * Downloads and reads the selected messages in parallel. A message that cannot be fetched or read
   * is logged and dropped from the result — the caller counts the shortfall as failed.
   */
  private List<FetchedMail> fetchAll(String accessToken, List<String> ids) {
    ExecutorService executor =
        Executors.newFixedThreadPool(Math.min(ids.size(), AUTO_IMPORT_PARALLELISM));
    try {
      return ids.stream()
          .map(id -> CompletableFuture.supplyAsync(() -> fetch(accessToken, id), executor))
          .toList()
          .stream()
          .map(CompletableFuture::join)
          .flatMap(Optional::stream)
          .toList();
    } finally {
      executor.shutdown();
    }
  }

  private Optional<FetchedMail> fetch(String accessToken, String messageId) {
    try {
      byte[] raw = gmailClient.getRaw(accessToken, messageId);
      return Optional.of(
          new FetchedMail(messageId, eventService.prepareMail(new ByteArrayInputStream(raw))));
    } catch (Exception exception) {
      log.warn("Auto-import: could not read message {}", messageId, exception);
      return Optional.empty();
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
