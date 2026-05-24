package com.sep.sep_backend.TripManagement.dto;

/* back-front relation: TripDTO is the JSON object the frontend receives for a trip.
Fields match the Trip interface in trip.service.ts */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import java.util.List;

public class TripDTO {
  private long id;
  private String name;
  private List<EventDTO> events;

  public static TripDTO from(Trip trip) {
    TripDTO dto = new TripDTO();
    dto.id = trip.getId();
    dto.name = trip.getName();

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

  public List<EventDTO> getEvents() {
    return events;
  }
}
