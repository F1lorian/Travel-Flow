package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Records that a specific Gmail message has already been imported as a calendar event for a user.
 *
 * <p>The automatic import scans the same inbox repeatedly, so this table is the persistent
 * de-duplication key: a {@code (user_id, message_id)} pair is stored the first time a message is
 * turned into an event, and every later scan skips messages that are already present. The unique
 * constraint makes a double import impossible even under a race.
 */
@Entity
@Table(
    name = "imported_gmail_messages",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "message_id"}))
public class ImportedGmailMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  /** Identifier of the local user the imported message belongs to. */
  @Column(name = "user_id", nullable = false)
  private long userId;

  /** Gmail message id that was imported. */
  @Column(name = "message_id", nullable = false)
  private String messageId;

  /** When the message was imported. */
  private Instant importedAt;

  public ImportedGmailMessage() {}

  public ImportedGmailMessage(long userId, String messageId) {
    this.userId = userId;
    this.messageId = messageId;
    this.importedAt = Instant.now();
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

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public Instant getImportedAt() {
    return importedAt;
  }

  public void setImportedAt(Instant importedAt) {
    this.importedAt = importedAt;
  }
}
