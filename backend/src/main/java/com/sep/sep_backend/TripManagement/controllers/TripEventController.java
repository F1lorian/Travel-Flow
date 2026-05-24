package com.sep.sep_backend.TripManagement.controllers;

/* back-front relation: REST endpoints for events within a trip.
GET  /api/trips/{tripId}/events  → frontend trip-detail page (loads events list)
POST /api/trips/{tripId}/events  → frontend add-event form */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.TripManagement.dto.EventDTO;
import com.sep.sep_backend.TripManagement.services.TripEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/events")
@CrossOrigin(origins = "*")
public class TripEventController {

    // Shape of the JSON body sent by the frontend when adding an event
    public record CreateEventRequest(String name, LocalDateTime startTime, LocalDateTime endTime, int kosten) {}

  private final TripEventService eventService;

  public TripEventController(TripEventService eventService) {
    this.eventService = eventService;
  }

  @GetMapping
  public List<EventDTO> getEvents(@PathVariable Long tripId) {
    return eventService.getEventsForTrip(tripId).stream().map(EventDTO::from).toList();
  }

    @PostMapping
    public ResponseEntity<EventDTO> addEvent(
            @PathVariable Long tripId,
            @RequestBody CreateEventRequest request) {
        Event created = eventService.addEventToTrip(tripId, request.name(), request.startTime(), request.endTime(), request.kosten());
        return ResponseEntity.ok(EventDTO.from(created));
    }
}
