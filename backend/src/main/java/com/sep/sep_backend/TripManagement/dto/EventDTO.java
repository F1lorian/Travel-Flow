package com.sep.sep_backend.TripManagement.dto;

/* back-front relation: EventDTO is the JSON object the frontend receives for an event.
Fields match the Event interface in trip.service.ts */

import com.sep.sep_backend.Enitys.Event;
import java.time.LocalDateTime;

public class EventDTO {
  private long id;
  private String name;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private int kosten; // cost in EUR (German "Kosten"); 0 = free
  private long tripId;
  private String categoryName;

  /**
   * Builds an EventDTO from an Event entity. The trip and category relations are flattened to a
   * plain {@code tripId} and {@code categoryName} so the client gets simple values instead of
   * nested entities.
   */
  public static EventDTO from(Event event) {
    EventDTO dto = new EventDTO();
    dto.id = event.getId();
    dto.name = event.getName();
    dto.startTime = event.getStartTime();
    dto.endTime = event.getEndTime();
    dto.kosten = event.getKosten();
    // Defensive null-checks: an event may have no trip/category set.
    dto.tripId = event.getTrip() != null ? event.getTrip().getId() : 0L;
    dto.categoryName = event.getCategorie() != null ? event.getCategorie().getName() : null;
    return dto;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public int getKosten() {
    return kosten;
  }

  public long getTripId() {
    return tripId;
  }

  public String getCategoryName() {
    return categoryName;
  }
}
