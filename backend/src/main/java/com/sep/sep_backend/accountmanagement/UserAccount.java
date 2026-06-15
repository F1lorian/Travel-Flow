package com.sep.sep_backend.accountmanagement;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_accounts",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = "username"),
      @UniqueConstraint(columnNames = "email")
    })
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String securityAnswerHash;

  @Column(nullable = false)
  private boolean emailVerified = false;

  private String verificationCode;
  private LocalDateTime verificationCodeExpiresAt;

  protected UserAccount() {}

  public UserAccount(
      String username, String email, String passwordHash, String securityAnswerHash) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.securityAnswerHash = securityAnswerHash;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getSecurityAnswerHash() {
    return securityAnswerHash;
  }

  public void setSecurityAnswerHash(String securityAnswerHash) {
    this.securityAnswerHash = securityAnswerHash;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public String getVerificationCode() {
    return verificationCode;
  }

  public void setVerificationCode(String verificationCode) {
    this.verificationCode = verificationCode;
  }

  public LocalDateTime getVerificationCodeExpiresAt() {
    return verificationCodeExpiresAt;
  }

  public void setVerificationCodeExpiresAt(LocalDateTime verificationCodeExpiresAt) {
    this.verificationCodeExpiresAt = verificationCodeExpiresAt;
  }
}
