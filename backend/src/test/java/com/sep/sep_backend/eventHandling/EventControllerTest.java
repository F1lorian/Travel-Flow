package com.sep.sep_backend.eventHandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.eventHandling.dto.DeleteEventResponseDTO;
import com.sep.sep_backend.eventHandling.dto.EventCalendarDTO;
import com.sep.sep_backend.eventHandling.dto.EventRequestDTO;
import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
import com.sep.sep_backend.repos.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

class EventControllerTest {

  @Test
  void eventControllerHatDenManuellenCreateEndpoint() throws NoSuchMethodException {
    assertThat(EventController.class.getAnnotation(RestController.class)).isNotNull();

    RequestMapping classMapping = EventController.class.getAnnotation(RequestMapping.class);
    assertThat(classMapping.value()).containsExactly("/api");

    Method method =
        EventController.class.getDeclaredMethod(
            "EventsManuellErstellen", EventRequestDTO.class, Authentication.class);

    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    assertThat(postMapping.value()).containsExactly("event/create/manual");
  }

  @Test
  void eventControllerHatDenAllEventsEndpoint() throws NoSuchMethodException {
    Method method = EventController.class.getDeclaredMethod("getAllEvents", Authentication.class);

    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    assertThat(getMapping.value()).containsExactly("event/pull/all_events");
  }

  @Test
  void eventControllerHatDenConflictEndpoint() throws NoSuchMethodException {
    Method method =
        EventController.class.getDeclaredMethod(
            "getConflictEvents", LocalDateTime.class, LocalDateTime.class, Authentication.class);

    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    assertThat(getMapping.value()).containsExactly("event/conflict");
  }

  @Test
  void eventControllerHatDenRemoveFriendFromEventEndpoint() throws NoSuchMethodException {
    Method method =
        EventController.class.getDeclaredMethod(
            "removeFriendFromEvent", Long.class, Long.class, Authentication.class);

    DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
    assertThat(deleteMapping.value())
        .containsExactly("event/{eventId}/participants/{friendUserId}");
  }

  @Test
  void getAllEventsGibtCalendarDtosAusServiceZurueck() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    List<EventCalendarDTO> events =
        List.of(
            new EventCalendarDTO(
                "42",
                "Museum",
                20,
                LocalDateTime.of(2026, 5, 7, 14, 30),
                LocalDateTime.of(2026, 5, 8, 14, 30),
                1L));

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.getAllEventsFromDb(7L)).thenReturn(events);

    ResponseEntity<List<EventCalendarDTO>> response = eventController.getAllEvents(authentication);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isEqualTo(events);
    verify(eventService).getAllEventsFromDb(7L);
  }

  @Test
  void eventsManuellErstellenGibtServiceResponseZurueck() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    EventRequestDTO request =
        new EventRequestDTO(
            "Museum",
            25,
            1L,
            LocalDateTime.of(2026, 6, 1, 14, 0),
            LocalDateTime.of(2026, 6, 1, 16, 0));
    EventResponseDTO created =
        new EventResponseDTO(
            "42",
            "Museum",
            25,
            LocalDateTime.of(2026, 6, 1, 14, 0),
            LocalDateTime.of(2026, 6, 1, 16, 0),
            1L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.SaveEventToDB(request, 7L)).thenReturn(created);

    ResponseEntity<EventResponseDTO> response =
        eventController.EventsManuellErstellen(request, authentication);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isSameAs(created);
    verify(eventService).SaveEventToDB(request, 7L);
  }

  @Test
  void getConflictEventsGibtServiceResponseZurueck() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 20, 0);
    LocalDateTime end = LocalDateTime.of(2026, 6, 1, 22, 0);
    List<EventResponseDTO> conflicts =
        List.of(
            new EventResponseDTO(
                "42",
                "Konzert",
                49,
                LocalDateTime.of(2026, 6, 1, 19, 30),
                LocalDateTime.of(2026, 6, 1, 21, 0),
                null));

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.conflictCheck(start, end, 7L)).thenReturn(conflicts);

    ResponseEntity<List<EventResponseDTO>> response =
        eventController.getConflictEvents(start, end, authentication);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isSameAs(conflicts);
    verify(eventService).conflictCheck(start, end, 7L);
  }

  @Test
  void parseEmailErlaubtImportOhneTripId() throws NoSuchMethodException {
    Method method =
        EventController.class.getDeclaredMethod(
            "parseEmail", MultipartFile.class, Long.class, Authentication.class);

    RequestParam tripIdParam = method.getParameters()[1].getAnnotation(RequestParam.class);

    assertThat(tripIdParam.value()).isEqualTo("tripId");
    assertThat(tripIdParam.required()).isFalse();
  }

  @Test
  void parseEmailGibtGeparstesEventZurueckOhneEsZuSpeichern() throws Exception {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    MultipartFile file = mock(MultipartFile.class);
    InputStream inputStream = new ByteArrayInputStream("mail".getBytes());
    Authentication authentication = authentication("Max");
    User user = user(7L);
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("Max", ""))
        .thenReturn(Optional.of(user));
    when(file.getInputStream()).thenReturn(inputStream);
    when(eventService.ParseMailFile(inputStream)).thenReturn(parsedEvent);

    EventResponseDTO response = eventController.parseEmail(file, null, authentication);

    assertThat(response).isSameAs(parsedEvent);
    verify(eventService).ParseMailFile(inputStream);
  }

  @Test
  void saveImportedEventSpeichertImportOhneTripId() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);
    EventResponseDTO savedEvent = new EventResponseDTO("1", "Konzert", 49, null, null, null);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.SaveEventResponseToDB(parsedEvent, null, 7L)).thenReturn(savedEvent);

    EventResponseDTO response =
        eventController.saveImportedEvent(parsedEvent, null, authentication);

    assertThat(response).isSameAs(savedEvent);
    verify(eventService).SaveEventResponseToDB(parsedEvent, null, 7L);
  }

  @Test
  void saveImportedEventSpeichertImportMitTripId() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);
    EventResponseDTO savedEvent = new EventResponseDTO("1", "Konzert", 49, null, null, 7L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.SaveEventResponseToDB(parsedEvent, 7L, 7L)).thenReturn(savedEvent);

    EventResponseDTO response = eventController.saveImportedEvent(parsedEvent, 7L, authentication);

    assertThat(response).isSameAs(savedEvent);
    verify(eventService).SaveEventResponseToDB(parsedEvent, 7L, 7L);
  }

  @Test
  void deleteEventFromDBGibtBestaetigungZurueck() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);

    ResponseEntity<DeleteEventResponseDTO> response = eventController.DeleteEventFromDB(42L);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody())
        .isEqualTo(new DeleteEventResponseDTO("Event erfolgreich gelöscht"));
    verify(eventService).DeleteEventFromDB(42L);
  }

  @Test
  void editEventGibtServiceResponseZurueck() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    EventRequestDTO request =
        new EventRequestDTO(
            "Theater",
            70,
            3L,
            LocalDateTime.of(2026, 7, 1, 19, 0),
            LocalDateTime.of(2026, 7, 1, 22, 0));
    EventResponseDTO edited =
        new EventResponseDTO(
            "42",
            "Theater",
            70,
            LocalDateTime.of(2026, 7, 1, 19, 0),
            LocalDateTime.of(2026, 7, 1, 22, 0),
            3L);

    when(eventService.EditEvent(42L, request)).thenReturn(edited);

    ResponseEntity<EventResponseDTO> response = eventController.EditEvent(42L, request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isSameAs(edited);
    verify(eventService).EditEvent(42L, request);
  }

  @Test
  void removeFriendFromEventDelegatesToServiceWithCurrentUser() {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));

    ResponseEntity<Void> response = eventController.removeFriendFromEvent(42L, 8L, authentication);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    verify(eventService).removeFriendFromEvent(42L, 8L, 7L);
  }

  @Test
  void eventControllerHatDenImapMessagesEndpoint() throws NoSuchMethodException {
    Method method =
        EventController.class.getDeclaredMethod(
            "getImapMessages", ImapMailboxRequestDTO.class, Authentication.class);

    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    assertThat(postMapping.value()).containsExactly("event/import/imap/messages");
  }

  @Test
  void eventControllerHatDenImapParseEndpoint() throws NoSuchMethodException {
    Method method =
        EventController.class.getDeclaredMethod(
            "parseSelectedImapMessage", ImapMailSelectionRequestDTO.class, Authentication.class);

    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    assertThat(postMapping.value()).containsExactly("event/import/imap/parse");
  }

  @Test
  void getImapMessagesDelegatesToService() throws Exception {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    ImapMailboxRequestDTO request =
        new ImapMailboxRequestDTO(
            "imap.example.com", 993, "mail@example.com", "secret", "INBOX", 50);
    List<ImapMailPreviewDTO> previews =
        List.of(new ImapMailPreviewDTO(123L, "Konzert", "tickets@example.com", null, null, ""));

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.getImapMailPreviews(request)).thenReturn(previews);

    List<ImapMailPreviewDTO> response = eventController.getImapMessages(request, authentication);

    assertThat(response).isSameAs(previews);
    verify(eventService).getImapMailPreviews(request);
  }

  @Test
  void parseSelectedImapMessageDelegatesToService() throws Exception {
    EventService eventService = mock(EventService.class);
    UserRepository userRepository = mock(UserRepository.class);
    EventController eventController = new EventController(eventService, userRepository);
    Authentication authentication = authentication("testuser");
    User user = user(7L);
    ImapMailSelectionRequestDTO request =
        new ImapMailSelectionRequestDTO(
            "imap.example.com", 993, "mail@example.com", "secret", "INBOX", 123L);
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user));
    when(eventService.parseImapMessage(request)).thenReturn(parsedEvent);

    EventResponseDTO response = eventController.parseSelectedImapMessage(request, authentication);

    assertThat(response).isSameAs(parsedEvent);
    verify(eventService).parseImapMessage(request);
  }

  private Authentication authentication(String username) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(username);
    return authentication;
  }

  private User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }
}
