package com.sep.sep_backend.exception;

/**
 * Thrown when the AI-based event detection could not run (e.g. the Gemini call failed or was rate
 * limited). Distinct from a genuine "this mail has no event" result so callers can report a message
 * as "could not be checked" instead of silently dropping it.
 */
public class EventDetectionException extends RuntimeException {

  /**
   * @param message why the detection could not run
   */
  public EventDetectionException(String message) {
    super(message);
  }

  /**
   * @param message why the detection could not run
   * @param cause the underlying failure, e.g. a transport error from the Gemini client
   */
  public EventDetectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
