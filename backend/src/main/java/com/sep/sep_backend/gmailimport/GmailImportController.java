package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.gmailimport.dto.CreatedEventDTO;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import com.sep.sep_backend.gmailimport.dto.GmailAuthUrlDTO;
import com.sep.sep_backend.gmailimport.dto.GmailAutoImportResultDTO;
import com.sep.sep_backend.gmailimport.dto.GmailCallbackRequestDTO;
import com.sep.sep_backend.gmailimport.dto.GmailConnectionStatusDTO;
import com.sep.sep_backend.gmailimport.dto.GmailImportRequestDTO;
import com.sep.sep_backend.repos.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for the Gmail import feature. The controller only resolves the current user and
 * delegates to {@link GmailImportService}; all business logic lives in the service layer.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/gmail")
public class GmailImportController {

  private static final Logger log = LoggerFactory.getLogger(GmailImportController.class);

  private final GmailImportService gmailImportService;
  private final UserRepository userRepository;

  public GmailImportController(
      GmailImportService gmailImportService, UserRepository userRepository) {
    this.gmailImportService = gmailImportService;
    this.userRepository = userRepository;
  }

  /** Returns whether the current user has connected a Gmail account. */
  @GetMapping("/status")
  public GmailConnectionStatusDTO status(Authentication authentication) {
    return gmailImportService.getStatus(currentUserId(authentication));
  }

  /** Returns the Google consent URL the frontend should redirect the user to. */
  @GetMapping("/auth-url")
  public GmailAuthUrlDTO authUrl(Authentication authentication) {
    return new GmailAuthUrlDTO(
        gmailImportService.buildAuthorizationUrl(currentUserId(authentication)));
  }

  /** Completes the OAuth flow using the authorization code returned by Google. */
  @PostMapping("/connect")
  public GmailConnectionStatusDTO connect(
      @RequestBody GmailCallbackRequestDTO request, Authentication authentication) {
    long userId = currentUserId(authentication);
    String email = gmailImportService.connect(userId, request.code());
    return new GmailConnectionStatusDTO(true, email);
  }

  /** Removes the current user's Gmail connection. */
  @PostMapping("/disconnect")
  public ResponseEntity<Void> disconnect(Authentication authentication) {
    gmailImportService.disconnect(currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  /** Scans recent emails and returns the extracted events awaiting confirmation. */
  @GetMapping("/scan")
  public List<ExtractedEventDTO> scan(Authentication authentication) {
    return gmailImportService.scanRecentEmails(currentUserId(authentication));
  }

  /**
   * Scans recent emails and immediately creates calendar events for confident detections, with no
   * review step. Re-running is safe: already-imported messages are skipped.
   */
  @PostMapping("/import-auto")
  public GmailAutoImportResultDTO importAuto(Authentication authentication) {
    return gmailImportService.scanAndImport(currentUserId(authentication));
  }

  /** Creates a calendar event from the values the user confirmed on the review screen. */
  @PostMapping("/import")
  public ResponseEntity<CreatedEventDTO> importEvent(
      @RequestBody GmailImportRequestDTO request, Authentication authentication) {
    CreatedEventDTO created =
        gmailImportService.createEvent(currentUserId(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  private long currentUserId(Authentication authentication) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
        .orElseThrow(() -> new IllegalArgumentException("User not found"))
        .getId();
  }

  // ---------------------------------------------------------------------------------------------
  // Error handling: each failure type maps to a meaningful HTTP status and a useful message.
  // ---------------------------------------------------------------------------------------------

  @ExceptionHandler(GmailAuthException.class)
  public ResponseEntity<Map<String, String>> handleAuth(GmailAuthException exception) {
    log.warn("Gmail auth error: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(exception.getMessage()));
  }

  @ExceptionHandler(GmailRateLimitException.class)
  public ResponseEntity<Map<String, String>> handleRateLimit(GmailRateLimitException exception) {
    log.warn("Gmail rate limit: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error(exception.getMessage()));
  }

  @ExceptionHandler(GmailApiException.class)
  public ResponseEntity<Map<String, String>> handleApi(GmailApiException exception) {
    log.error("Gmail API error", exception);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(error(exception.getMessage()));
  }

  private Map<String, String> error(String message) {
    return Map.of("message", message == null ? "Unexpected error" : message);
  }
}
