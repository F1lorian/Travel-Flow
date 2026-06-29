package com.sep.sep_backend.EventHandling;

import java.time.LocalDateTime;

/** Response data transfer object containing event values returned to clients. */
public class EventResponseDTO {
  private String id;
  private String name;
  private double kosten;
  private Long tripId;
  private LocalDateTime start;
  private LocalDateTime end;

  /** Creates an empty event response DTO for serialization and deserialization. */
  public EventResponseDTO() {}

  /**
   * Creates an event response DTO without an event identifier or trip identifier.
   *
   * @param name name of the event
   * @param kosten costs assigned to the event
   * @param start start date and time of the event
   * @param end end date and time of the event
   */
  public EventResponseDTO(String name, double kosten, LocalDateTime start, LocalDateTime end) {
    this(null, name, kosten, start, end, null);
  }

  /**
   * Creates an event response DTO with all event values returned by the API.
   *
   * @param id event identifier
   * @param name name of the event
   * @param kosten costs assigned to the event
   * @param start start date and time of the event
   * @param end end date and time of the event
   * @param tripId identifier of the trip the event belongs to
   */
  public EventResponseDTO(
      String id, String name, double kosten, LocalDateTime start, LocalDateTime end, Long tripId) {
    this.id = id;
    this.name = name;
    this.kosten = kosten;
    this.tripId = tripId;
    this.start = start;
    this.end = end;
  }

  /**
   * Returns the event identifier.
   *
   * @return event identifier
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the event identifier.
   *
   * @param id event identifier
   */
  public void setId(String id) {
    this.id = id;
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
