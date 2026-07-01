package com.sep.sep_backend.gmailimport;

/**
 * Thrown when a call to the Gmail API fails for a reason other than authentication, for example an
 * unexpected HTTP status or an unreadable response body.
 */
public class GmailApiException extends RuntimeException {

  public GmailApiException(String message) {
    super(message);
  }

  public GmailApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
