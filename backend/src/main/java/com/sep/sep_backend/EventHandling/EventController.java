package com.sep.sep_backend.EventHandling;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

  //Zum Befüllen des Kalenders
  @GetMapping("event/pull/all_events")
  public ResponseEntity<List<EventCalendarDTO>> getAllEvents() {
    return ResponseEntity.ok(eventService.GetAllEventsFromDb());
  }

  //Nimmt ein eml File entgegen (Multipart email)
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "event/create/import")
  public EventResponseDTO uploadEmail(
      @RequestParam("MailFile") MultipartFile file, @RequestParam("tripId") Long tripId)
      throws Exception {
    //Inputstream Notwendig für jakarta
    var eventDTO = eventService.ParseMailFile(file.getInputStream());
    return eventService.SaveEventResponseToDB(eventDTO, tripId);
  }

  @PostMapping("event/delete")
  public ResponseEntity<DeleteEventResponseDTO> DeleteEventFromDB(@RequestBody Long EventID) {
    eventService.DeleteEventFromDB(EventID);
    return ResponseEntity.ok(new DeleteEventResponseDTO("Event erfolgreich gelöscht"));
  }

  @PostMapping("event/edit/{id}")
  public ResponseEntity<EventResponseDTO> EditEvent(
      @PathVariable Long id, @RequestBody EventRequestDTO dto) {
    var response = eventService.EditEvent(id, dto);
    return ResponseEntity.ok(response);
  }
}
