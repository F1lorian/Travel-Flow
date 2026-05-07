package com.sep.sep_backend.TripManagement.controllers;

/* back-front relation: REST endpoints for events within a trip.
   GET  /api/trips/{tripId}/events  → frontend trip-detail page (loads events list)
   POST /api/trips/{tripId}/events  → frontend add-event form */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.TripManagement.dto.EventDTO;
import com.sep.sep_backend.TripManagement.services.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/events")
@CrossOrigin(origins = "*")
public class EventController {

    // Shape of the JSON body sent by the frontend when adding an event
    public record CreateEventRequest(String name, LocalDateTime dateTime, int kosten) {}

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventDTO> getEvents(@PathVariable Long tripId) {
        return eventService.getEventsForTrip(tripId)
                .stream()
                .map(EventDTO::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<EventDTO> addEvent(
            @PathVariable Long tripId,
            @RequestBody CreateEventRequest request) {
        Event created = eventService.addEventToTrip(tripId, request.name(), request.dateTime(), request.kosten());
        return ResponseEntity.ok(EventDTO.from(created));
    }
}