package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
import com.sep.sep_backend.exception.EventDetectionException;
import com.sep.sep_backend.gmailimport.dto.GmailAutoImportRequestDTO;
import com.sep.sep_backend.gmailimport.dto.GmailAutoImportResultDTO;
import com.sep.sep_backend.gmailimport.dto.GmailConnectRequestDTO;
import com.sep.sep_backend.gmailimport.dto.GmailConnectionDTO;
import com.sep.sep_backend.gmailimport.dto.GmailMessagePreviewDTO;
import com.sep.sep_backend.gmailimport.dto.GmailParseRequestDTO;
import com.sep.sep_backend.repos.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** REST controller for connecting a Gmail account and importing events from its messages. */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/gmail")
public class GmailImportController {

  private final GmailImportService gmailImportService;
  private final UserRepository userRepository;

  public GmailImportController(
      GmailImportService gmailImportService, UserRepository userRepository) {
    this.gmailImportService = gmailImportService;
    this.userRepository = userRepository;
  }

  /** Completes the OAuth connect using the authorization code returned to the frontend. */
  @PostMapping("/connect")
  public GmailConnectionDTO connect(
      @RequestBody GmailConnectRequestDTO request, Authentication authentication) {
    return gmailImportService.connect(getCurrentUserId(authentication), request.code());
  }

  /** Reports whether the current user has a connected Gmail account. */
  @GetMapping("/status")
  public GmailConnectionDTO status(Authentication authentication) {
    return gmailImportService.status(getCurrentUserId(authentication));
  }

  /** Lists the most recent messages from the connected mailbox for the import picker. */
  @GetMapping("/messages")
  public List<GmailMessagePreviewDTO> messages(Authentication authentication) {
    return gmailImportService.listMessages(getCurrentUserId(authentication));
  }

  /** Parses the selected message into an (unsaved) event that the client can then save. */
  @PostMapping("/import/parse")
  public EventResponseDTO parse(
      @RequestBody GmailParseRequestDTO request, Authentication authentication) throws Exception {
    return gmailImportService.parseMessage(getCurrentUserId(authentication), request.messageId());
  }

  /**
   * Parses the selected messages for automatic import and returns only those that contain an event,
   * so non-event mails are not turned into calendar entries.
   */
  @PostMapping("/import/auto")
  public GmailAutoImportResultDTO autoImport(
      @RequestBody GmailAutoImportRequestDTO request, Authentication authentication) {
    return gmailImportService.autoParse(getCurrentUserId(authentication), request.messageIds());
  }

  /** Removes the stored Gmail connection for the current user. */
  @DeleteMapping("/disconnect")
  public ResponseEntity<Void> disconnect(Authentication authentication) {
    gmailImportService.disconnect(getCurrentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  private Long getCurrentUserId(Authentication authentication) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
        .orElseThrow(() -> new IllegalArgumentException("User not found"))
        .getId();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> handleUpstreamFailure(IllegalStateException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(exception.getMessage());
  }

  @ExceptionHandler(EventDetectionException.class)
  public ResponseEntity<String> handleDetectionFailure(EventDetectionException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(exception.getMessage());
  }
}
