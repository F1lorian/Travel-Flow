package com.sep.sep_backend.gmailimport;

/**
 * Thrown when a Gmail OAuth operation fails: the user has not connected Gmail, the stored refresh
 * token is invalid or revoked, or the token exchange with Google fails.
 */
public class GmailAuthException extends RuntimeException {

  public GmailAuthException(String message) {
    super(message);
  }

  public GmailAuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
