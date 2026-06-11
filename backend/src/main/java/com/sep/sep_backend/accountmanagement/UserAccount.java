package com.sep.sep_backend.accountmanagement;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_accounts",
    uniqueConstraints = {@UniqueConstraint(columnNames = "username")})
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String securityAnswerHash;

  @Column(nullable = false)
  private boolean emailVerified = true;

  private String verificationCode;
  private LocalDateTime verificationCodeExpiresAt;
  protected UserAccount() {}

  public UserAccount(String username, String passwordHash, String securityAnswerHash) {
    this.username = username;
    this.email = username.toLowerCase() + "@sep.local";
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

}
