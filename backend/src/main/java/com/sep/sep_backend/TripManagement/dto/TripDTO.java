package com.sep.sep_backend.TripManagement.dto;

/* back-front relation: TripDTO is the JSON object the frontend receives for a trip.
Fields match the Trip interface in trip.service.ts */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import java.time.LocalDate;
import java.util.List;

/**
 * API response shape for a trip, including its events as {@link EventDTO}s, so the frontend
 * receives a trip and all its events in a single JSON object. Fields mirror the {@code Trip}
 * interface in {@code trip.service.ts}.
 */
public class TripDTO {
  private long id;
  private String name;
  private LocalDate startDate;
  private LocalDate endDate;
  private List<EventDTO> events;

  /**
   * Builds a TripDTO from a Trip entity, also converting its events to {@link EventDTO}s so the
   * whole trip (with its events) serializes in one response.
   */
  public static TripDTO from(Trip trip) {
    TripDTO dto = new TripDTO();
    dto.id = trip.getId();
    dto.name = trip.getName();
    dto.startDate = trip.getStartDate();
    dto.endDate = trip.getEndDate();

    // Guard against a null events list so the JSON is always an array, never null.
    List<Event> tripEvents = trip.getEvents();
    if (tripEvents == null) {
      dto.events = List.of();
    } else {
      dto.events = tripEvents.stream().map(EventDTO::from).toList();
    }
    return dto;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public List<EventDTO> getEvents() {
    return events;
  }
}
