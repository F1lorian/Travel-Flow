package com.sep.sep_backend.eventHandling;

import java.time.LocalDateTime;

/** Data transfer object for events displayed in the frontend calendar. */
public class EventCalendarDTO {
  private String id;
  private String title;
  private double kosten;
  private Long tripId;
  private LocalDateTime start;
  private LocalDateTime end;

  /** Creates an empty calendar event DTO for serialization and deserialization. */
  public EventCalendarDTO() {}

  /**
   * Creates a calendar event DTO with all values needed by the calendar view.
   *
   * @param id event identifier used by the frontend
   * @param title display title of the event
   * @param kosten costs assigned to the event
   * @param start start date and time of the event
   * @param end end date and time of the event
   * @param tripId identifier of the trip the event belongs to
   */
  public EventCalendarDTO(
      String id, String title, double kosten, LocalDateTime start, LocalDateTime end, Long tripId) {
    this.id = id;
    this.title = title;
    this.start = start;
    this.end = end;
    this.kosten = kosten;
    this.tripId = tripId;
  }

  /**
   * Returns the event identifier used by the calendar.
   *
   * @return event identifier as text
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the event identifier used by the calendar.
   *
   * @param id event identifier as text
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the display title of the event.
   *
   * @return event title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Sets the display title of the event.
   *
   * @param title event title
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Returns the start date and time of the event.
   *
   * @return event start date and time
   */
  public LocalDateTime getStart() {
    return start;
  }

  /**
   * Sets the start date and time of the event.
   *
   * @param start event start date and time
   */
  public void setStart(LocalDateTime start) {
    this.start = start;
  }

  /**
   * Returns the end date and time of the event.
   *
   * @return event end date and time
   */
  public LocalDateTime getEnd() {
    return this.end;
  }

  /**
   * Sets the end date and time of the event.
   *
   * @param end event end date and time
   */
  public void setEnd(LocalDateTime end) {
    this.end = end;
  }

  /**
   * Returns the costs assigned to the event.
   *
   * @return event costs
   */
  public double getKosten() {
    return this.kosten;
  }

  /**
   * Sets the costs assigned to the event.
   *
   * @param kosten event costs
   */
  public void setKosten(double kosten) {
    this.kosten = kosten;
  }

  /**
   * Returns the identifier of the trip the event belongs to.
   *
   * @return trip identifier
   */
  public Long getTripId() {
    return this.tripId;
  }

  /**
   * Sets the identifier of the trip the event belongs to.
   *
   * @param tripId trip identifier
   */
  public void setTripId(Long tripId) {
    this.tripId = tripId;
  }
}
