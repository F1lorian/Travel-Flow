package com.sep.sep_backend.exception;

import com.sep.sep_backend.accountmanagement.MessageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns exceptions thrown by any controller or service into JSON error responses of the shape
 * {@code {"message": "..."}} so the frontend can show the user why an action failed (e.g. "Trip
 * name cannot be empty"). Controllers that declare their own {@code @ExceptionHandler} (account
 * management) keep precedence over these application-wide fallbacks.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Domain rule violation: an event's dates lie outside its trip's date range. */
  @ExceptionHandler(EventOutsideTripException.class)
  public ResponseEntity<String> handleEventOutsideTrip(EventOutsideTripException ex) {

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  /** Invalid input (blank name, bad date range, ...) becomes 400 with the validation message. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<MessageResponseDTO> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(new MessageResponseDTO(ex.getMessage()));
  }

  /**
   * Services signal missing resources (404), forbidden access (403), and missing authentication
   * (401) with {@link ResponseStatusException}. Spring hides the reason from the response body by
   * default; this handler keeps the status code but exposes the reason as a readable message.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<MessageResponseDTO> handleResponseStatus(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(new MessageResponseDTO(ex.getReason()));
  }
}
