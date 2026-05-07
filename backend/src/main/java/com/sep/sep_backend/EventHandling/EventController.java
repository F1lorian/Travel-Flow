package com.sep.sep_backend.EventHandling;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("event/create/manual")
    public ResponseEntity<EventResponseDTO> EventsManuellErstellen(@RequestBody EventRequestDTO dto) {
            var responsedto = eventService.SaveEventToDB(dto);
            return ResponseEntity.ok().body(responsedto);
    }


    @GetMapping("event/pull/all_events")
    public ResponseEntity<List<EventCalendarDTO>> getAllEvents() {
        return ResponseEntity.ok(eventService.GetAllEventsFromDb());
    }
}
