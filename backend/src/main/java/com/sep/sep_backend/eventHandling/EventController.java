package com.sep.sep_backend.eventHandling;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.eventHandling.dto.DeleteEventResponseDTO;
import com.sep.sep_backend.eventHandling.dto.EventCalendarDTO;
import com.sep.sep_backend.eventHandling.dto.EventInviteDTO;
import com.sep.sep_backend.eventHandling.dto.EventRequestDTO;
import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
import com.sep.sep_backend.eventHandling.dto.UserEventDTO;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for event creation, import, editing, deletion, conflict checks, and invitation
 * handling.
 */
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
   * @param userRepository repository used to resolve the authenticated user id
   */
  public EventController(EventService eventService, UserRepository userRepository) {
    this.eventService = eventService;
    this.userRepository = userRepository;
  }

  /**
   * Creates an event from manually entered event data.
   *
   * @param dto event data sent by the client
   * @param authentication current authenticated user
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
   * @param authentication current authenticated user
   * @return response containing all calendar events visible to the user
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
   * @param authentication current authenticated user
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
   * Lists recent IMAP messages from the mailbox provided in the request.
   *
   * @param request IMAP connection data, optional folder, and optional result limit
   * @param authentication current authenticated user
   * @return message previews that can be selected for import
   * @throws Exception if the mailbox cannot be accessed
   */
  @PostMapping("event/import/imap/messages")
  public List<ImapMailPreviewDTO> getImapMessages(
      @RequestBody ImapMailboxRequestDTO request, Authentication authentication) throws Exception {
    getCurrentUserId(authentication);
    return eventService.getImapMailPreviews(request);
  }

  /**
   * Parses a selected IMAP message into event data without saving it.
   *
   * @param request IMAP connection data and message UID
   * @param authentication current authenticated user
   * @return extracted event values
   * @throws Exception if the message cannot be loaded or parsed
   */
  @PostMapping("event/import/imap/parse")
  public EventResponseDTO parseSelectedImapMessage(
      @RequestBody ImapMailSelectionRequestDTO request, Authentication authentication)
      throws Exception {
    getCurrentUserId(authentication);
    return eventService.parseImapMessage(request);
  }

  /**
   * Saves a previously parsed imported event and optionally assigns it to a trip.
   *
   * @param dto parsed event values sent by the client after conflict handling
   * @param tripId optional identifier of the trip the imported event belongs to
   * @param authentication current authenticated user
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
   * @param authentication current authenticated user
   * @return response containing a deletion confirmation message
   */
  @PostMapping("event/delete")
  public ResponseEntity<DeleteEventResponseDTO> DeleteEventFromDB(
      @RequestBody Long EventID, Authentication authentication) {
    eventService.DeleteEventFromDB(EventID, getCurrentUserId(authentication));
    return ResponseEntity.ok(new DeleteEventResponseDTO("Event erfolgreich gelöscht"));
  }

  /**
   * Updates an existing event with the provided event data.
   *
   * @param id identifier of the event to update
   * @param dto event values that should be applied
   * @param authentication current authenticated user
   * @return response containing the updated event data
   */
  @PostMapping("event/edit/{id}")
  public ResponseEntity<EventResponseDTO> EditEvent(
      @PathVariable Long id, @RequestBody EventRequestDTO dto, Authentication authentication) {
    var response = eventService.EditEvent(id, dto, getCurrentUserId(authentication));
    return ResponseEntity.ok(response);
  }

  /**
   * Returns stored events that overlap the given time interval.
   *
   * @param start start date and time of the event being checked
   * @param end end date and time of the event being checked
   * @param authentication current authenticated user
   * @return response containing all conflicting events for the authenticated user
   */
  @GetMapping("event/conflict")
  public ResponseEntity<List<EventResponseDTO>> getConflictEvents(
      @RequestParam LocalDateTime start,
      @RequestParam LocalDateTime end,
      Authentication authentication) {
    return ResponseEntity.ok(
        eventService.conflictCheck(start, end, getCurrentUserId(authentication)));
  }

  /**
   * Invites a friend to an event owned by the authenticated user.
   *
   * @param eventId event to invite the friend to
   * @param friendUserId id of the invited friend
   * @param authentication current authenticated user
   * @return empty success response
   */
  @PostMapping("event/{eventId}/invite/{friendUserId}")
  public ResponseEntity<Void> inviteFriendToEvent(
      @PathVariable Long eventId, @PathVariable Long friendUserId, Authentication authentication) {
    Long userId = getCurrentUserId(authentication);
    eventService.inviteFriendToEvent(eventId, friendUserId, userId);
    return ResponseEntity.ok().build();
  }

  /**
   * Removes a participant from an event.
   *
   * <p>Owners can remove other participants; invited users can remove themselves.
   *
   * @param eventId event from which the participant should be removed
   * @param friendUserId id of the participant to remove
   * @param authentication current authenticated user
   * @return empty no-content response on success
   */
  @DeleteMapping("event/{eventId}/participants/{friendUserId}")
  public ResponseEntity<Void> removeFriendFromEvent(
      @PathVariable Long eventId, @PathVariable Long friendUserId, Authentication authentication) {
    Long userId = getCurrentUserId(authentication);
    eventService.removeFriendFromEvent(eventId, friendUserId, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Stores whether the authenticated user accepts or declines an event invite.
   *
   * @param eventId invited event identifier
   * @param status final invite status; {@code PENDING} is rejected by the service
   * @param authentication current authenticated user
   * @return empty success response
   */
  @PutMapping("event/{eventId}/respond")
  public ResponseEntity<Void> respondToInvite(
      @PathVariable Long eventId,
      @RequestParam UserEvent.InviteStatus status,
      Authentication authentication) {
    Long userId = getCurrentUserId(authentication);
    eventService.respondToInvite(eventId, userId, status);
    return ResponseEntity.ok().build();
  }

  /**
   * Lists invites for one event that were sent by the authenticated user.
   *
   * @param eventId event whose outgoing invites should be listed
   * @param authentication current authenticated user
   * @return response containing outgoing invitation DTOs
   */
  @GetMapping("event/{eventId}/invites")
  public ResponseEntity<List<UserEventDTO>> getInvitesSentByUser(
      @PathVariable Long eventId, Authentication authentication) {
    Long userId = getCurrentUserId(authentication);
    List<UserEventDTO> dtos =
        eventService.getInvitesSentByUser(eventId, userId).stream()
            .map(UserEventDTO::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /**
   * Lists every participant row for an event.
   *
   * @param eventId event whose participants should be listed
   * @return response containing participant DTOs
   */
  @GetMapping("event/{eventId}/participants")
  public ResponseEntity<List<UserEventDTO>> getParticipants(@PathVariable Long eventId) {
    List<UserEventDTO> dtos =
        eventService.getParticipants(eventId).stream().map(UserEventDTO::from).toList();
    return ResponseEntity.ok(dtos);
  }

  /**
   * Lists pending event invitations for the authenticated user.
   *
   * @param authentication current authenticated user
   * @return response containing incoming event invite DTOs
   */
  @GetMapping("event/invites/incoming")
  public ResponseEntity<List<EventInviteDTO>> getIncomingEventInvites(
      Authentication authentication) {
    Long userId = getCurrentUserId(authentication);
    List<EventInviteDTO> dtos =
        eventService.getIncomingEventInvites(userId).stream().map(EventInviteDTO::from).toList();
    return ResponseEntity.ok(dtos);
  }

  /**
   * Resolves the authenticated user id from the Spring Security context.
   *
   * @param authentication current authenticated user
   * @return id of the authenticated user
   */
  private Long getCurrentUserId(Authentication authentication) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
        .orElseThrow(() -> new IllegalArgumentException("User not found"))
        .getId();
  }
}
