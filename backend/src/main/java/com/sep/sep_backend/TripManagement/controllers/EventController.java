package com.sep.sep_backend.TripManagement.controllers;

/* back-front relation: REST endpoints for events within a trip.
   GET  /api/trips/{tripId}/events  → frontend trip-detail page (loads events list)
   POST /api/trips/{tripId}/events  → frontend add-event form */

import com.sep.sep_backend.TripManagement.dto.EventDTO;
import com.sep.sep_backend.TripManagement.services.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trips/{tripId}/events")
@CrossOrigin(origins = "*")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventDTO> getEvents(@PathVariable Long tripId) {
        return eventService.getEventsForTrip(tripId)
                .stream()
                .map(EventDTO::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<EventDTO> addEvent(
            @PathVariable Long tripId,
            @RequestBody Map<String, Object> body) {

        String name = (String) body.get("name");
        LocalDateTime dateTime = LocalDateTime.parse((String) body.get("dateTime"));
        int kosten = (int) body.getOrDefault("kosten", 0);

        EventDTO dto = EventDTO.from(eventService.addEventToTrip(tripId, name, dateTime, kosten));
        return ResponseEntity.ok(dto);
    }
}