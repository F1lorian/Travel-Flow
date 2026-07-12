package com.sep.sep_backend.tripManagement.controllers;

/* back-front relation: REST endpoints for events within a trip.
GET  /api/trips/{tripId}/events  → frontend trip-detail page (loads events list)
POST /api/trips/{tripId}/events  → frontend add-event form */

import com.sep.sep_backend.accountmanagement.CurrentUserService;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.tripManagement.dto.EventDTO;
import com.sep.sep_backend.tripManagement.services.TripEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for the events that belong to a trip: list, add, delete, and move an event to
 * another trip. Delegates to {@link TripEventService} (which checks trip membership for every
 * operation) and returns {@link EventDTO}s.
 *
 * <p>Errors are not handled here: they bubble up to {@link
 * com.sep.sep_backend.exception.GlobalExceptionHandler}, which turns them into {@code {"message":
 * "..."}} responses the frontend shows to the user.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/events")
public class TripEventController {

  /** Shape of the JSON body sent by the frontend when adding or updating a trip event. */
  public record CreateEventRequest(
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int kosten,
      String categoryName) {}

  private final TripEventService eventService;
  private final CurrentUserService currentUserService;

  public TripEventController(TripEventService eventService, CurrentUserService currentUserService) {
    this.eventService = eventService;
    this.currentUserService = currentUserService;
  }

  /** GET /api/trips/{tripId}/events — all events of one trip the user is a member of. */
  @GetMapping
  public List<EventDTO> getEvents(@PathVariable Long tripId, Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    return eventService.getEventsForTrip(tripId, userId).stream().map(EventDTO::from).toList();
  }

  /** POST /api/trips/{tripId}/events — add an event; invalid input becomes 400 with a reason. */
  @PostMapping
  public EventDTO addEvent(
      @PathVariable Long tripId,
      @RequestBody CreateEventRequest request,
      Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    Event created =
        eventService.addEventToTrip(
            tripId,
            userId,
            request.name(),
            request.startTime(),
            request.endTime(),
            request.kosten(),
            request.categoryName());
    return EventDTO.from(created);
  }

  /**
   * DELETE /api/trips/{tripId}/events/{eventId} — 204 on success, 404 if the event is unknown.
   * tripId is part of the URL for REST nesting but the event is located by its own id; membership
   * is checked against the trip the event actually belongs to.
   */
  @DeleteMapping("/{eventId}")
  public ResponseEntity<Void> deleteEvent(
      @PathVariable Long tripId, @PathVariable Long eventId, Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    eventService.deleteEvent(eventId, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * PUT /api/trips/{tripId}/events/{eventId}/move/{newTripId} — reassign an event to another trip
   * (used by the "move to another trip" flow on the trip-detail page).
   */
  @PutMapping("/{eventId}/move/{newTripId}")
  public EventDTO moveEvent(
      @PathVariable Long tripId,
      @PathVariable Long eventId,
      @PathVariable Long newTripId,
      Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    return EventDTO.from(eventService.moveEventToTrip(eventId, newTripId, userId));
  }
}
