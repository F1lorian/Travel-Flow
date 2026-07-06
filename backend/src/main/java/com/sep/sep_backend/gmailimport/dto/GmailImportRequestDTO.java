package com.sep.sep_backend.gmailimport.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The event values the user confirmed (and possibly edited) on the confirmation screen. Sent back
 * to the backend when the user clicks "Create Event".
 */
public class GmailImportRequestDTO {

  private String title;
  private String description;
  private LocalDate date;
  private LocalTime startTime;
  private LocalTime endTime;

  /** Cost of the event in EUR; 0 means free/no price. */
  private double kosten;

  /** Optional trip the created event should be attached to. */
  private Long tripId;

  public GmailImportRequestDTO() {}

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

  public double getKosten() {
    return kosten;
  }

  public void setKosten(double kosten) {
    this.kosten = kosten;
  }

  public Long getTripId() {
    return tripId;
  }

  public void setTripId(Long tripId) {
    this.tripId = tripId;
  }
}
