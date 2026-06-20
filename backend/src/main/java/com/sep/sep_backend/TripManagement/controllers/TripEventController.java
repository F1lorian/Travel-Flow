package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.TripManagement.dto.EventDTO;
import com.sep.sep_backend.TripManagement.services.TripEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/events")
@CrossOrigin(origins = "*")
public class TripEventController {

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

  @GetMapping
  public List<EventDTO> getEvents(
      @PathVariable Long tripId, @AuthenticationPrincipal String username) {
    eventService.verifyTripOwnership(tripId, username);
    return eventService.getEventsForTrip(tripId).stream().map(EventDTO::from).toList();
  }

  @PostMapping
  public ResponseEntity<EventDTO> addEvent(
      @PathVariable Long tripId,
      @RequestBody CreateEventRequest request,
      @AuthenticationPrincipal String username) {
    eventService.verifyTripOwnership(tripId, username);
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

  @DeleteMapping("/{eventId}")
  public ResponseEntity<Void> deleteEvent(
      @PathVariable Long tripId,
      @PathVariable Long eventId,
      @AuthenticationPrincipal String username) {
    eventService.verifyTripOwnership(tripId, username);
    try {
      eventService.deleteEvent(eventId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  @PutMapping("/{eventId}/move/{newTripId}")
  public ResponseEntity<EventDTO> moveEvent(
      @PathVariable Long tripId,
      @PathVariable Long eventId,
      @PathVariable Long newTripId,
      @AuthenticationPrincipal String username) {
    eventService.verifyTripOwnership(tripId, username);
    eventService.verifyTripOwnership(newTripId, username);
    try {
      Event moved = eventService.moveEventToTrip(eventId, newTripId);
      return ResponseEntity.ok(EventDTO.from(moved));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
