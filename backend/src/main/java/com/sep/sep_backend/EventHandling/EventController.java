package com.sep.sep_backend.EventHandling;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "event/create/automatic")
    public EventResponseDTO uploadEmail(@RequestParam("MailFile") MultipartFile file, @RequestParam("tripId") Long tripId) throws Exception
    {
        var eventDTO = eventService.ParseMailFile(file.getInputStream());
        eventService.SaveEventResponseToDB(eventDTO, tripId);
        return eventDTO;
    }
}
