package com.sep.sep_backend.EventHandling;

import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** REST controller for creating, importing, listing, editing, and deleting events. */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api")
public class EventController {

  private final EventService eventService;
  private final UserRepository userRepository;

  /**
   * Creates an event controller that delegates event operations to the service layer.
   *
   * @param eventService service used to process event requests
   */
  public EventController(EventService eventService, UserRepository userRepository) {
    this.eventService = eventService;
    this.userRepository = userRepository;
  }

  /**
   * Creates an event from manually entered event data.
   *
   * @param dto event data sent by the client
   * @return response containing the created event data
   */
  @PostMapping("event/create/manual")
  public ResponseEntity<EventResponseDTO> EventsManuellErstellen(
      @RequestBody EventRequestDTO dto, Authentication authentication) {
    User user =
        userRepository
            .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    var responsedto = eventService.SaveEventToDB(dto, user.getId());
    return ResponseEntity.ok().body(responsedto);
  }

  /**
   * Returns all stored events in the format required by the calendar view.
   *
   * @return response containing all calendar events
   */
  @GetMapping("event/pull/all_events")
  public ResponseEntity<List<EventCalendarDTO>> getAllEvents(Authentication authentication) {
    User user =
        userRepository
            .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return ResponseEntity.ok(eventService.getAllEventsFromDb(user.getId()));
  }

  /**
   * Parses an event from an uploaded email file without saving it.
   *
   * @param file uploaded email file containing event information
   * @param tripId optional trip identifier sent by the client; saving happens in a later request
   * @return response containing the parsed event values
   * @throws Exception if the uploaded email cannot be read or parsed
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "event/import/parse")
  public EventResponseDTO parseEmail(
      @RequestParam("MailFile") MultipartFile file,
      @RequestParam(value = "tripId", required = false) Long tripId,
      Authentication authentication)
      throws Exception {
    User user =
        userRepository
            .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    // Inputstream Notwendig für jakarta
    return eventService.ParseMailFile(file.getInputStream());
  }

  /**
   * Saves a previously parsed imported event and optionally assigns it to a trip.
   *
   * @param dto parsed event values sent by the client after conflict handling
   * @param tripId optional identifier of the trip the imported event belongs to
   * @return response containing the saved event data
   */
  @PostMapping("event/create/import")
  public EventResponseDTO saveImportedEvent(
      @RequestBody EventResponseDTO dto,
      @RequestParam(value = "tripId", required = false) Long tripId,
      Authentication authentication) {
    User user =
        userRepository
            .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return eventService.SaveEventResponseToDB(dto, tripId, user.getId());
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

  /**
   * Returns stored events that overlap the given time interval.
   *
   * @param start start date and time of the event being checked
   * @param end end date and time of the event being checked
   * @return response containing all conflicting events
   */
  @GetMapping("event/conflict")
  public ResponseEntity<List<EventResponseDTO>> getConflictEvents(
      @RequestParam LocalDateTime start, @RequestParam LocalDateTime end) {
    return ResponseEntity.ok(eventService.conflictCheck(start, end));
  }

  @PostMapping("event/{eventId}/invite/{friendUserId}")
  public ResponseEntity<Void> inviteFriendToEvent(
          @PathVariable Long eventId, @PathVariable Long friendUserId) {
    eventService.addUserToEvent(eventId, friendUserId);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("event/{eventId}/invite/{friendUserId}")
  public ResponseEntity<Void> removeFriendFromEvent(
          @PathVariable Long eventId, @PathVariable Long friendUserId) {
    eventService.removeUserFromEvent(eventId, friendUserId);
    return ResponseEntity.ok().build();
  }
}
