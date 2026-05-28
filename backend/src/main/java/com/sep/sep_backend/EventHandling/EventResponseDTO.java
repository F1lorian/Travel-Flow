package com.sep.sep_backend.EventHandling;

import java.time.LocalDateTime;

public class EventResponseDTO {
  private String id;
  private String name;
  private int kosten;
  private long tripId;
  private LocalDateTime start;
  private LocalDateTime end;

  public EventResponseDTO() {}

  public EventResponseDTO(String name, int kosten, LocalDateTime start, LocalDateTime end) {
    this(null, name, kosten, start, end, 0);
  }

  public EventResponseDTO(
      String id, String name, int kosten, LocalDateTime start, LocalDateTime end, long tripId) {
    this.id = id;
    this.name = name;
    this.kosten = kosten;
    this.tripId = tripId;
    this.start = start;
    this.end = end;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getKosten() {
    return kosten;
  }

  public void setKosten(int kosten) {
    this.kosten = kosten;
  }

  public long getTripId() {
    return tripId;
  }

  public void setTripId(long tripId) {
    this.tripId = tripId;
  }

  public LocalDateTime getStart() {
    return start;
  }

  public void setStart(LocalDateTime start) {
    this.start = start;
  }

  public LocalDateTime getEnd() {
    return end;
  }

  public void setEnd(LocalDateTime end) {
    this.end = end;
  }
}
