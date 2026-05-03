package com.sep.sep_backend.controllers;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.services.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips/{tripId}/events")
@CrossOrigin(origins = "*")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // POST /api/trips/{tripId}/events → add a new event to a trip
    @PostMapping
    public ResponseEntity<Event> addEvent(
            @PathVariable Long tripId,
            @RequestBody Map<String, Object> body) {

        String name = (String) body.get("name");
        LocalDateTime dateTime = LocalDateTime.parse((String) body.get("dateTime"));
        int kosten = (int) body.getOrDefault("kosten", 0);

        Event event = eventService.addEventToTrip(tripId, name, dateTime, kosten);
        return ResponseEntity.ok(event);
    }

    // GET /api/trips/{tripId}/events → list events for a trip
    @GetMapping
    public List<Event> getEvents(@PathVariable Long tripId) {
        return eventService.getEventsForTrip(tripId);
    }
}