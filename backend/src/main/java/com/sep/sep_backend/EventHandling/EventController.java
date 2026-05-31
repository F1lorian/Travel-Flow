package com.sep.sep_backend.EventHandling;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for creating, importing, listing, editing, and deleting events.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api")
public class EventController {

  private final EventService eventService;

  /**
   * Creates an event controller that delegates event operations to the service layer.
   *
   * @param eventService service used to process event requests
   */
  public EventController(EventService eventService) {
    this.eventService = eventService;
  }

  /**
   * Creates an event from manually entered event data.
   *
   * @param dto event data sent by the client
   * @return response containing the created event data
   */
  @PostMapping("event/create/manual")
  public ResponseEntity<EventResponseDTO> EventsManuellErstellen(@RequestBody EventRequestDTO dto) {
    var responsedto = eventService.SaveEventToDB(dto);
    return ResponseEntity.ok().body(responsedto);
  }

  // Zum Befüllen des Kalenders
  /**
   * Returns all stored events in the format required by the calendar view.
   *
   * @return response containing all calendar events
   */
  @GetMapping("event/pull/all_events")
  public ResponseEntity<List<EventCalendarDTO>> getAllEvents() {
    return ResponseEntity.ok(eventService.GetAllEventsFromDb());
  }

  // Nimmt ein eml File entgegen (Multipart email)
  /**
   * Imports an event from an uploaded email file and assigns it to a trip.
   *
   * @param file uploaded email file containing event information
   * @param tripId identifier of the trip the imported event belongs to
   * @return response containing the imported and saved event data
   * @throws Exception if the uploaded email cannot be read or parsed
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "event/create/import")
  public EventResponseDTO uploadEmail(
      @RequestParam("MailFile") MultipartFile file,
      @RequestParam(value = "tripId", required = false) Long tripId)
      throws Exception {
    // Inputstream Notwendig für jakarta
    var eventDTO = eventService.ParseMailFile(file.getInputStream());
    return eventService.SaveEventResponseToDB(eventDTO, tripId);
  }

  /**
   * Deletes the event with the given identifier.
   *
   * @param EventID identifier of the event to delete
   * @return response containing a deletion confirmation message
   */
  @PostMapping("event/delete")
  public ResponseEntity<DeleteEventResponseDTO> DeleteEventFromDB(@RequestBody Long EventID) {
    eventService.DeleteEventFromDB(EventID);
    return ResponseEntity.ok(new DeleteEventResponseDTO("Event erfolgreich gelöscht"));
  }

  /**
   * Updates an existing event with the provided event data.
   *
   * @param id identifier of the event to update
   * @param dto event values that should be applied
   * @return response containing the updated event data
   */
  @PostMapping("event/edit/{id}")
  public ResponseEntity<EventResponseDTO> EditEvent(
      @PathVariable Long id, @RequestBody EventRequestDTO dto) {
    var response = eventService.EditEvent(id, dto);
    return ResponseEntity.ok(response);
  }
}
