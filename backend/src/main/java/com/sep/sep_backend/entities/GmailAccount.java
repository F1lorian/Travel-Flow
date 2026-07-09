package com.sep.sep_backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * A Gmail account a user connected for event import. Holds the encrypted OAuth refresh token so the
 * user only has to grant access once; access tokens are derived from it on demand and never stored.
 */
@Entity
@Table(name = "gmail_accounts")
public class GmailAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  /** Owning user; a user connects at most one Gmail account. */
  @Column(nullable = false, unique = true)
  private long userId;

  /** The Gmail address that granted access, shown back to the user. */
  @Column(nullable = false)
  private String gmailEmail;

  /** OAuth refresh token, encrypted at rest via {@code TokenCipher}. */
  @Column(nullable = false, length = 2048)
  private String encryptedRefreshToken;

  private String scope;

  @Column(nullable = false)
  private LocalDateTime connectedAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  public GmailAccount() {}

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

  public String getGmailEmail() {
    return gmailEmail;
  }

  public void setGmailEmail(String gmailEmail) {
    this.gmailEmail = gmailEmail;
  }

  public String getEncryptedRefreshToken() {
    return encryptedRefreshToken;
  }

  public void setEncryptedRefreshToken(String encryptedRefreshToken) {
    this.encryptedRefreshToken = encryptedRefreshToken;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public LocalDateTime getConnectedAt() {
    return connectedAt;
  }

  public void setConnectedAt(LocalDateTime connectedAt) {
    this.connectedAt = connectedAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
