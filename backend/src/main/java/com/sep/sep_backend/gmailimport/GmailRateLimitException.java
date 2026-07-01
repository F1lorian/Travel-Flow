package com.sep.sep_backend.gmailimport;

/**
 * Thrown when the Gmail API rejects a request because the user- or project-level rate limit has
 * been exceeded (HTTP 429). Surfaced to the client as HTTP 429 so the UI can ask the user to retry
 * later.
 */
public class GmailRateLimitException extends RuntimeException {

  public GmailRateLimitException(String message) {
    super(message);
  }
}
