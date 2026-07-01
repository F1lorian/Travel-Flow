package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import com.sep.sep_backend.gmailimport.dto.GmailImportRequestDTO;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/** Tests that the controller delegates to the service and maps failures to useful HTTP statuses. */
class GmailImportControllerTest {

  private final GmailImportService service = mock(GmailImportService.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final GmailImportController controller =
      new GmailImportController(service, userRepository);

  @Test
  void scanDelegatesToServiceForCurrentUser() {
    Authentication authentication = authentication("alice");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("alice", ""))
        .thenReturn(Optional.of(user(7L)));
    ExtractedEventDTO event = new ExtractedEventDTO();
    event.setTitle("Concert");
    event.setDate(LocalDate.of(2026, 6, 1));
    when(service.scanRecentEmails(7L)).thenReturn(List.of(event));

    List<ExtractedEventDTO> result = controller.scan(authentication);

    assertThat(result).extracting(ExtractedEventDTO::getTitle).containsExactly("Concert");
    verify(service).scanRecentEmails(7L);
  }

  @Test
  void importReturnsCreatedStatus() {
    Authentication authentication = authentication("alice");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("alice", ""))
        .thenReturn(Optional.of(user(7L)));

    ResponseEntity<?> response =
        controller.importEvent(new GmailImportRequestDTO(), authentication);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void authErrorMapsToUnauthorized() {
    ResponseEntity<?> response = controller.handleAuth(new GmailAuthException("not connected"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void rateLimitErrorMapsToTooManyRequests() {
    ResponseEntity<?> response =
        controller.handleRateLimit(new GmailRateLimitException("slow down"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void apiErrorMapsToBadGateway() {
    ResponseEntity<?> response = controller.handleApi(new GmailApiException("boom"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void invalidArgumentMapsToBadRequest() {
    ResponseEntity<?> response =
        controller.handleBadRequest(new IllegalArgumentException("A date is required"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private Authentication authentication(String name) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(name);
    return authentication;
  }

  private User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }
}
