package com.sep.sep_backend.gmailimport.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Event information extracted from a single Gmail message and shown on the confirmation screen.
 *
 * <p>Every field is optional: when a piece of information cannot be found in the email it is left
 * {@code null} instead of guessed, so the user can fill it in on the confirmation form. The parser
 * never throws when data is missing.
 */
public class ExtractedEventDTO {

  /** Gmail message id the event was extracted from, used for de-duplication and traceability. */
  private String sourceMessageId;

  private String title;
  private String description;
  private LocalDate date;
  private LocalTime startTime;
  private LocalTime endTime;
  private String timeZone;
  private String location;
  private String organizer;

  public ExtractedEventDTO() {}

  public String getSourceMessageId() {
    return sourceMessageId;
  }

  public void setSourceMessageId(String sourceMessageId) {
    this.sourceMessageId = sourceMessageId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalTime endTime) {
    this.endTime = endTime;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getOrganizer() {
    return organizer;
  }

  public void setOrganizer(String organizer) {
    this.organizer = organizer;
  }
}
