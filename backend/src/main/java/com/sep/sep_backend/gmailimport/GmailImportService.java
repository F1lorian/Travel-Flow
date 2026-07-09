package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.entities.GmailAccount;
import com.sep.sep_backend.eventHandling.EventResponseDTO;
import com.sep.sep_backend.eventHandling.EventService;
import com.sep.sep_backend.gmailimport.dto.GmailConnectionDTO;
import com.sep.sep_backend.gmailimport.dto.GmailMessagePreviewDTO;
import com.sep.sep_backend.repos.GmailAccountRepository;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates Gmail import for a user: stores the connection (encrypted refresh token), lists
 * recent messages, and parses a chosen message into an event using the shared mail parser.
 */
@Service
public class GmailImportService {

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
