package com.sep.sep_backend.trip_management.controllers;

/* back-front relation: REST endpoints for events within a trip.
GET  /api/trips/{tripId}/events  → frontend trip-detail page (loads events list)
POST /api/trips/{tripId}/events  → frontend add-event form */

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.trip_management.dto.EventDTO;
import com.sep.sep_backend.trip_management.services.TripEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for the events that belong to a trip: list, add, delete, and move an event to
 * another trip. Delegates to {@link TripEventService} and returns {@link EventDTO}s.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/events")
@CrossOrigin(origins = "*")
public class TripEventController {

  // Shape of the JSON body sent by the frontend when adding an event
  public record CreateEventRequest(
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int kosten,
      String categoryName) {}

  private final TripEventService eventService;

  public TripEventController(TripEventService eventService) {
    this.eventService = eventService;
  }

  // GET /api/trips/{tripId}/events — all events belonging to one trip, as DTOs.
  @GetMapping
  public List<EventDTO> getEvents(@PathVariable Long tripId) {
    return eventService.getEventsForTrip(tripId).stream().map(EventDTO::from).toList();
  }

  // POST /api/trips/{tripId}/events — add an event to the trip. Invalid input
  // (e.g. blank name or unknown trip) becomes HTTP 400.
  @PostMapping
  public ResponseEntity<EventDTO> addEvent(
      @PathVariable Long tripId, @RequestBody CreateEventRequest request) {
    try {
      Event created =
          eventService.addEventToTrip(
              tripId,
              request.name(),
              request.startTime(),
              request.endTime(),
              request.kosten(),
              request.categoryName());
      return ResponseEntity.ok(EventDTO.from(created));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  // DELETE /api/trips/{tripId}/events/{eventId} — 204 on success, 404 if not found.
  // tripId is part of the URL for REST nesting but the event is deleted by its own id.
  @DeleteMapping("/{eventId}")
  public ResponseEntity<Void> deleteEvent(@PathVariable Long tripId, @PathVariable Long eventId) {
    try {
      eventService.deleteEvent(eventId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  // PUT /api/trips/{tripId}/events/{eventId}/move/{newTripId} — reassign an event to
  // another trip (used by the "move to another trip" flow on the trip-detail page).
  @PutMapping("/{eventId}/move/{newTripId}")
  public ResponseEntity<EventDTO> moveEvent(
      @PathVariable Long tripId, @PathVariable Long eventId, @PathVariable Long newTripId) {
    try {
      Event moved = eventService.moveEventToTrip(eventId, newTripId);
      return ResponseEntity.ok(EventDTO.from(moved));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
