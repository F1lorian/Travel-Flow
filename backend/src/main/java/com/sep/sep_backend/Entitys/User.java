package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  @Column(unique = true, nullable = false)
  private String name;

  private String firstName;

  private String surname;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  private LocalDate dateOfBirth;

  private String securityQuestion;

  private String securityAnswerHash;

  @Column(nullable = false)
  private boolean emailVerified = false;

  private String verificationCode;

  private LocalDateTime verificationCodeExpiresAt;

  @ManyToMany
  @JoinTable(
      name = "user_trips",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "trip_id"))
  private List<Trip> trips;

  public User() {}

  public User(String name, String email, String passwordHash, String securityAnswerHash) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.securityAnswerHash = securityAnswerHash;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getSurname() {
    return surname;
  }

  public void setSurname(String surname) {
    this.surname = surname;
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

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getSecurityQuestion() {
    return securityQuestion;
  }

  public void setSecurityQuestion(String securityQuestion) {
    this.securityQuestion = securityQuestion;
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

  public List<Trip> getTrips() {
    return trips;
  }

  public void setTrips(List<Trip> trips) {
    this.trips = trips;
  }
}
