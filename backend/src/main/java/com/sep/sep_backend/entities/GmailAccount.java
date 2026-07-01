package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Stores the OAuth 2.0 tokens that let the backend read a single user's Gmail mailbox.
 *
 * <p>The access and refresh tokens are persisted in encrypted form (see {@code TokenCipher}); this
 * entity never holds the plaintext values. Each user can connect at most one Gmail account, which
 * is enforced by the unique {@code user_id} column.
 */
@Entity
@Table(name = "gmail_accounts")
public class GmailAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  /** Identifier of the local user that owns this Gmail connection. */
  @Column(name = "user_id", nullable = false, unique = true)
  private long userId;

  /** Gmail address that was connected, shown to the user in the UI. */
  private String emailAddress;

  /** Encrypted OAuth access token used for Gmail API calls. */
  @Column(length = 4096)
  private String accessTokenEncrypted;

  /** Encrypted OAuth refresh token used to obtain new access tokens. */
  @Column(length = 4096)
  private String refreshTokenEncrypted;

  /** Point in time at which the current access token expires. */
  private Instant accessTokenExpiresAt;

  public GmailAccount() {}

  public GmailAccount(long userId) {
    this.userId = userId;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getAccessTokenEncrypted() {
    return accessTokenEncrypted;
  }

  public void setAccessTokenEncrypted(String accessTokenEncrypted) {
    this.accessTokenEncrypted = accessTokenEncrypted;
  }

  public String getRefreshTokenEncrypted() {
    return refreshTokenEncrypted;
  }

  public void setRefreshTokenEncrypted(String refreshTokenEncrypted) {
    this.refreshTokenEncrypted = refreshTokenEncrypted;
  }

  public Instant getAccessTokenExpiresAt() {
    return accessTokenExpiresAt;
  }

  public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
    this.accessTokenExpiresAt = accessTokenExpiresAt;
  }
}
