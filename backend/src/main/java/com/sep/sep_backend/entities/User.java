package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** JPA entity for application accounts, including profile fields, login state, and friendships. */
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

  /** Authentication origin of the account: "LOCAL" for password accounts, "GOOGLE" for OAuth. */
  private String provider = "LOCAL";

  private LocalDate dateOfBirth;

  private String securityQuestion;

  private String securityAnswerHash;

  @Column(nullable = false)
  private boolean emailVerified = false;

  private String verificationCode;

  private LocalDateTime verificationCodeExpiresAt;

  @OneToMany(mappedBy = "user")
  private List<UserTrip> userTrips;

  @ManyToMany
  @JoinTable(
      name = "user_events",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "event_id"))
  private List<Event> events;

  @ManyToMany
  @JoinTable(
      name = "user_friends",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "friend_id"))
  private Set<User> friends = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private List<Ausgabe> ausgaben;

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

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
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

  public List<UserTrip> getUserTrips() {
    return userTrips;
  }

  public void setUserTrips(List<UserTrip> userTrips) {
    this.userTrips = userTrips;
  }

  public List<Event> getEvents() {
    return events;
  }

  public void setEvents(List<Event> events) {
    this.events = events;
  }

  public Set<User> getFriends() {
    return friends;
  }

  public void setFriends(Set<User> friends) {
    this.friends = friends;
  }

  public List<Ausgabe> getAusgaben() {
    return ausgaben;
  }

  public void setAusgaben(List<Ausgabe> ausgaben) {
    this.ausgaben = ausgaben;
  }
}
