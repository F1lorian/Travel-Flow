package com.sep.sep_backend.eventHandling.dto;

import java.time.LocalDateTime;

/** Request data transfer object for manually creating or editing an event. */
public class EventRequestDTO {
  private String name;
  private double kosten;
  private Long tripId;
  private String categoryName;
  private LocalDateTime start;
  private LocalDateTime end;

  /** Creates an empty event request DTO for serialization and deserialization. */
  public EventRequestDTO() {}

  /**
   * Creates an event request DTO with all editable event values.
   *
   * @param name name of the event
   * @param kosten costs assigned to the event
   * @param tripId identifier of the trip the event belongs to
   * @param start start date and time of the event
   * @param end end date and time of the event
   */
  public EventRequestDTO(
      String name, double kosten, Long tripId, LocalDateTime start, LocalDateTime end) {
    this.name = name;
    this.kosten = kosten;
    this.tripId = tripId;
    this.start = start;
    this.end = end;
  }

  /**
   * Creates an event request DTO without explicit start and end dates.
   *
   * @param name name of the event
   * @param kosten costs assigned to the event
   * @param tripId identifier of the trip the event belongs to
   */
  public EventRequestDTO(String name, double kosten, Long tripId) {
    this(name, kosten, tripId, null, null);
  }

  /**
   * Returns the event name.
   *
   * @return event name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the event name.
   *
   * @param name event name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the costs assigned to the event.
   *
   * @return event costs
   */
  public double getKosten() {
    return kosten;
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
    return tripId;
  }

  /**
   * Sets the identifier of the trip the event belongs to.
   *
   * @param tripId trip identifier
   */
  public void setTripId(Long tripId) {
    this.tripId = tripId;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String categoryName) {
    this.categoryName = categoryName;
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
    return end;
  }

  /**
   * Sets the end date and time of the event.
   *
   * @param end event end date and time
   */
  public void setEnd(LocalDateTime end) {
    this.end = end;
  }
}
