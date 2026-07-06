package com.sep.sep_backend.eventHandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.exception.EventOutsideTripException;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  @Mock private EventRepository eventRepository;

  @Mock private UserEventRepository userEventRepository;

  @Mock private TripRepository tripRepository;

  @Mock private AusgabeService ausgabeService;

  @Mock private UserRepository userRepository;

  @Mock private ObjectMapper objectMapper;

  @InjectMocks private EventService eventService;

  @Test
  void saveEventToDBSpeichertEventFuerVorhandeneTripId() throws Exception {
    Trip trip = new Trip("London");
    EventRequestDTO request = new EventRequestDTO("Museum", 25, 1L);
    LocalDateTime beforeSave = LocalDateTime.now();

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEvents(7L)));

    eventService.SaveEventToDB(request, 7L);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());

    Event savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getName()).isEqualTo("Museum");
    assertThat(savedEvent.getKosten()).isEqualTo(25);
    assertThat(savedEvent.getTrip()).isSameAs(trip);
    assertThat(savedEvent.getStartTime()).isAfterOrEqualTo(beforeSave);
  }

  @Test
  void saveEventToDBSpeichertEventOhneTripId() {
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 20, 0);
    LocalDateTime end = LocalDateTime.of(2026, 6, 1, 21, 0);
    EventRequestDTO request = new EventRequestDTO("Museum", 25, null, start, end);

    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    User user = userWithEvents(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    EventResponseDTO result = eventService.SaveEventToDB(request, 7L);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());

    Event savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getName()).isEqualTo("Museum");
    assertThat(savedEvent.getTrip()).isNull();
    assertThat(savedEvent.getTripId()).isNull();
    assertThat(result.getTripId()).isNull();

    ArgumentCaptor<UserEvent> userEventCaptor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository).save(userEventCaptor.capture());
    assertThat(userEventCaptor.getValue().getEvent()).isSameAs(savedEvent);
    assertThat(userEventCaptor.getValue().getUser()).isSameAs(user);
    assertThat(userEventCaptor.getValue().getCreator()).isSameAs(user);
    assertThat(userEventCaptor.getValue().getStatus()).isEqualTo(UserEvent.InviteStatus.ACCEPTED);
  }

  @Test
  void saveEventToDBWirftExceptionWennTripNichtExistiert() {
    EventRequestDTO request = new EventRequestDTO("Museum", 25, 99L);
    when(tripRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> eventService.SaveEventToDB(request, 7L))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Zu dieser trip Id findet sich kein eintrag in der Datenbank");

    verify(eventRepository, never()).save(any(Event.class));
  }

  @Test
  void saveEventToDBWirftExceptionWennEventVorTripStartLiegt() {
    Trip trip = new Trip("London");
    trip.setStartDate(LocalDate.of(2026, 6, 10));
    trip.setEndDate(LocalDate.of(2026, 6, 20));
    EventRequestDTO request =
        new EventRequestDTO(
            "Museum",
            25,
            1L,
            LocalDateTime.of(2026, 6, 9, 23, 0),
            LocalDateTime.of(2026, 6, 10, 1, 0));

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> eventService.SaveEventToDB(request, 7L))
        .isInstanceOf(EventOutsideTripException.class)
        .hasMessage("Event liegt außerhalb des Trips");

    verify(eventRepository, never()).save(any(Event.class));
  }

  @Test
  void saveEventResponseToDBWirftExceptionWennEventNachTripEndeLiegt() {
    Trip trip = new Trip("London");
    trip.setStartDate(LocalDate.of(2026, 6, 10));
    trip.setEndDate(LocalDate.of(2026, 6, 20));
    EventResponseDTO responseDTO =
        new EventResponseDTO(
            "Konzert",
            49,
            LocalDateTime.of(2026, 6, 20, 20, 0),
            LocalDateTime.of(2026, 6, 21, 1, 0));

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> eventService.SaveEventResponseToDB(responseDTO, 1L, 7L))
        .isInstanceOf(EventOutsideTripException.class)
        .hasMessage("Event liegt außerhalb des Trips");

    verify(eventRepository, never()).save(any(Event.class));
  }

  @Test
  void saveEventResponseToDBSpeichertEventFuerVorhandeneTripId() {
    Trip trip = new Trip("London");
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 20, 0);
    LocalDateTime end = LocalDateTime.of(2026, 6, 1, 21, 0);
    EventResponseDTO responseDTO = new EventResponseDTO("Konzert", 49, start, end);

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    User user = userWithEvents(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    EventResponseDTO result = eventService.SaveEventResponseToDB(responseDTO, 1L, 7L);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());

    Event savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getName()).isEqualTo("Konzert");
    assertThat(savedEvent.getKosten()).isEqualTo(49);
    assertThat(savedEvent.getStartTime()).isEqualTo(start);
    assertThat(savedEvent.getEndTime()).isEqualTo(end);
    assertThat(savedEvent.getTrip()).isSameAs(trip);
    assertThat(result.getName()).isEqualTo("Konzert");
    assertThat(result.getKosten()).isEqualTo(49);

    ArgumentCaptor<UserEvent> userEventCaptor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository).save(userEventCaptor.capture());
    assertThat(userEventCaptor.getValue().getEvent()).isSameAs(savedEvent);
    assertThat(userEventCaptor.getValue().getUser()).isSameAs(user);
    assertThat(userEventCaptor.getValue().getStatus()).isEqualTo(UserEvent.InviteStatus.ACCEPTED);
  }

  @Test
  void saveEventResponseToDBNutztFallbackNameFuerLeerenImportNamen() {
    EventResponseDTO responseDTO =
        new EventResponseDTO("", 49, LocalDateTime.of(2026, 6, 1, 20, 0), null);

    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    User user = userWithEvents(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    EventResponseDTO result = eventService.SaveEventResponseToDB(responseDTO, null, 7L);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());

    assertThat(eventCaptor.getValue().getName()).isEqualTo("Unbekanntes Event");
    assertThat(result.getName()).isEqualTo("Unbekanntes Event");

    ArgumentCaptor<UserEvent> userEventCaptor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository).save(userEventCaptor.capture());
    assertThat(userEventCaptor.getValue().getEvent()).isSameAs(eventCaptor.getValue());
    assertThat(userEventCaptor.getValue().getUser()).isSameAs(user);
    assertThat(userEventCaptor.getValue().getStatus()).isEqualTo(UserEvent.InviteStatus.ACCEPTED);
  }

  @Test
  void saveEventResponseToDBSpeichertEventOhneTripId() {
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 20, 0);
    LocalDateTime end = LocalDateTime.of(2026, 6, 1, 21, 0);
    EventResponseDTO responseDTO = new EventResponseDTO("Konzert", 49, start, end);

    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    User user = userWithEvents(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    EventResponseDTO result = eventService.SaveEventResponseToDB(responseDTO, null, 7L);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());

    Event savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getName()).isEqualTo("Konzert");
    assertThat(savedEvent.getTrip()).isNull();
    assertThat(savedEvent.getTripId()).isNull();
    assertThat(result.getTripId()).isNull();

    ArgumentCaptor<UserEvent> userEventCaptor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository).save(userEventCaptor.capture());
    assertThat(userEventCaptor.getValue().getEvent()).isSameAs(savedEvent);
    assertThat(userEventCaptor.getValue().getUser()).isSameAs(user);
    assertThat(userEventCaptor.getValue().getCreator()).isSameAs(user);
    assertThat(userEventCaptor.getValue().getStatus()).isEqualTo(UserEvent.InviteStatus.ACCEPTED);
  }

  @Test
  void getAllEventsFromDbWandeltEventsInCalendarDTOsUm() {
    Trip trip = new Trip("London");
    LocalDateTime start = LocalDateTime.of(2026, 5, 7, 14, 30);
    LocalDateTime end = LocalDateTime.of(2026, 5, 8, 14, 30);
    Event event = new Event("Museum", start, end, 25, trip);
    event.setId(42L);

    when(userEventRepository.findAllByUserId(7L)).thenReturn(List.of(event));

    List<EventCalendarDTO> result = eventService.getAllEventsFromDb(7L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo("42");
    assertThat(result.getFirst().getTitle()).isEqualTo("Museum");
    assertThat(result.getFirst().getStart()).isEqualTo(start);
    verify(userEventRepository).findAllByUserId(7L);
  }

  @Test
  void getAllEventsFromDbGibtNullTripIdFuerEventsOhneTripZurueck() {
    LocalDateTime start = LocalDateTime.of(2026, 5, 7, 14, 30);
    LocalDateTime end = LocalDateTime.of(2026, 5, 8, 14, 30);
    Event event = new Event("Museum", start, end, 25, null);
    event.setId(42L);

    when(userEventRepository.findAllByUserId(7L)).thenReturn(List.of(event));

    List<EventCalendarDTO> result = eventService.getAllEventsFromDb(7L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getTripId()).isNull();
    verify(userEventRepository).findAllByUserId(7L);
  }

  @Test
  void conflictCheckGibtUeberlappendeEventsAlsResponseDtosZurueck() {
    LocalDateTime requestedStart = LocalDateTime.of(2026, 6, 1, 20, 0);
    LocalDateTime requestedEnd = LocalDateTime.of(2026, 6, 1, 22, 0);
    Trip trip = new Trip("London");
    trip.setId(7L);
    Event event =
        new Event(
            "Konzert",
            LocalDateTime.of(2026, 6, 1, 19, 30),
            LocalDateTime.of(2026, 6, 1, 21, 0),
            49,
            trip);
    event.setId(42L);

    when(eventRepository.findConflictingEventsForUser(requestedStart, requestedEnd, 7L))
        .thenReturn(List.of(event));

    List<EventResponseDTO> result = eventService.conflictCheck(requestedStart, requestedEnd, 7L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo("42");
    assertThat(result.getFirst().getName()).isEqualTo("Konzert");
    assertThat(result.getFirst().getKosten()).isEqualTo(49);
    assertThat(result.getFirst().getStart()).isEqualTo(event.getStartTime());
    assertThat(result.getFirst().getEnd()).isEqualTo(event.getEndTime());
    assertThat(result.getFirst().getTripId()).isEqualTo(7L);
    verify(eventRepository).findConflictingEventsForUser(requestedStart, requestedEnd, 7L);
  }

  @Test
  void conflictCheckGibtLeereListeZurueckWennKeineEventsUeberlappen() {
    LocalDateTime requestedStart = LocalDateTime.of(2026, 6, 1, 20, 0);
    LocalDateTime requestedEnd = LocalDateTime.of(2026, 6, 1, 22, 0);

    when(eventRepository.findConflictingEventsForUser(requestedStart, requestedEnd, 7L))
        .thenReturn(List.of());

    List<EventResponseDTO> result = eventService.conflictCheck(requestedStart, requestedEnd, 7L);

    assertThat(result).isEmpty();
    verify(eventRepository).findConflictingEventsForUser(requestedStart, requestedEnd, 7L);
  }

  @Test
  void deleteEventFromDBLoeschtVorhandenesEvent() {
    when(eventRepository.existsById(42L)).thenReturn(true);

    eventService.DeleteEventFromDB(42L);

    InOrder inOrder = inOrder(userEventRepository, eventRepository);
    inOrder.verify(userEventRepository).deleteByEvent_Id(42L);
    inOrder.verify(eventRepository).deleteById(42L);
  }

  @Test
  void deleteEventFromDBWirftExceptionWennEventNichtExistiert() {
    when(eventRepository.existsById(42L)).thenReturn(false);

    assertThatThrownBy(() -> eventService.DeleteEventFromDB(42L))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Event mit ID 42 wurde nicht gefunden");

    verify(eventRepository, never()).deleteById(any());
    verify(userEventRepository, never()).deleteByEvent_Id(any());
  }

  @Test
  void removeParticipantFromEventDeletesInviteCreatedByRequestingUser() {
    Event event = new Event();
    User participant = userWithEvents(8L);
    User creator = userWithEvents(7L);
    UserEvent userEvent = new UserEvent(event, participant, creator);

    when(userEventRepository.findByEvent_IdAndUser_Id(42L, 8L))
        .thenReturn(Optional.of(userEvent));

    eventService.removeParticipantFromEvent(42L, 8L, 7L);

    verify(userEventRepository).delete(userEvent);
  }

  @Test
  void removeParticipantFromEventRejectsOtherRequestingUser() {
    Event event = new Event();
    User participant = userWithEvents(8L);
    User creator = userWithEvents(7L);
    UserEvent userEvent = new UserEvent(event, participant, creator);

    when(userEventRepository.findByEvent_IdAndUser_Id(42L, 8L))
        .thenReturn(Optional.of(userEvent));

    assertThatThrownBy(() -> eventService.removeParticipantFromEvent(42L, 8L, 9L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(userEventRepository, never()).delete(any(UserEvent.class));
  }

  @Test
  void editEventAktualisiertGesetzteWerteUndTrip() {
    Trip oldTrip = new Trip("London");
    Trip newTrip = new Trip("Paris");
    newTrip.setId(7L);
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 6, 1, 14, 0),
            LocalDateTime.of(2026, 6, 1, 16, 0),
            25,
            oldTrip);
    event.setId(42L);
    EventRequestDTO request =
        new EventRequestDTO(
            "Theater",
            70,
            7L,
            LocalDateTime.of(2026, 6, 2, 19, 0),
            LocalDateTime.of(2026, 6, 2, 22, 0));

    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));
    when(tripRepository.findById(7L)).thenReturn(Optional.of(newTrip));
    when(eventRepository.save(event)).thenReturn(event);

    EventResponseDTO result = eventService.EditEvent(42L, request);

    assertThat(event.getName()).isEqualTo("Theater");
    assertThat(event.getKosten()).isEqualTo(70);
    assertThat(event.getStartTime()).isEqualTo(LocalDateTime.of(2026, 6, 2, 19, 0));
    assertThat(event.getEndTime()).isEqualTo(LocalDateTime.of(2026, 6, 2, 22, 0));
    assertThat(event.getTrip()).isSameAs(newTrip);
    assertThat(result.getTripId()).isEqualTo(7L);
    verify(eventRepository).save(event);
  }

  @Test
  void editEventLaesstKostenUnveraendertWennMinusEinsGesendetWird() {
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 6, 1, 14, 0),
            LocalDateTime.of(2026, 6, 1, 16, 0),
            25,
            null);
    EventRequestDTO request = new EventRequestDTO(null, -1, null, null, null);

    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));
    when(eventRepository.save(event)).thenReturn(event);

    EventResponseDTO result = eventService.EditEvent(42L, request);

    assertThat(event.getKosten()).isEqualTo(25);
    assertThat(result.getKosten()).isEqualTo(25);
    verify(tripRepository, never()).findById(any());
    verify(eventRepository).save(event);
  }

  @Test
  void editEventWirftExceptionWennEventNichtExistiert() {
    when(eventRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> eventService.EditEvent(42L, new EventRequestDTO()))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Event mit ID 42 wurde nicht gefunden");

    verify(eventRepository, never()).save(any(Event.class));
  }

  @Test
  void parseMailFileLiestDirekteJsonLdEventDaten() throws Exception {
    EventService serviceWithObjectMapper = serviceWithRealObjectMapper();
    String json =
        """
        {
          "@context": "https://schema.org",
          "@type": "Event",
          "name": "Konzert",
          "startDate": "2026-06-01T20:00:00",
          "endDate": "2026-06-01T22:00:00",
          "offers": { "price": 49 }
        }
        """;

    EventResponseDTO result = serviceWithObjectMapper.ParseMailFile(jsonLdMail(json));

    assertThat(result.getName()).isEqualTo("Konzert");
    assertThat(result.getKosten()).isEqualTo(49);
    assertThat(result.getStart()).isEqualTo(LocalDateTime.of(2026, 6, 1, 20, 0));
    assertThat(result.getEnd()).isEqualTo(LocalDateTime.of(2026, 6, 1, 22, 0));
  }

  @Test
  void parseMailFileLiestJsonLdEventAusGraph() throws Exception {
    EventService serviceWithObjectMapper = serviceWithRealObjectMapper();
    String json =
        """
        {
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Event",
              "name": "Graph Konzert",
              "startDate": "2026-07-01T20:00:00",
              "endDate": "2026-07-01T22:00:00",
              "offers": { "price": 33 }
            }
          ]
        }
        """;

    EventResponseDTO result = serviceWithObjectMapper.ParseMailFile(jsonLdMail(json));

    assertThat(result.getName()).isEqualTo("Graph Konzert");
    assertThat(result.getKosten()).isEqualTo(33);
    assertThat(result.getStart()).isEqualTo(LocalDateTime.of(2026, 7, 1, 20, 0));
    assertThat(result.getEnd()).isEqualTo(LocalDateTime.of(2026, 7, 1, 22, 0));
  }

  @Test
  void parseMailFileNutztTextFallbackOhneGeminiApiKey() throws Exception {
    EventService serviceWithObjectMapper = serviceWithRealObjectMapper();
    LocalDateTime beforeParse = LocalDateTime.now();

    EventResponseDTO result =
        serviceWithObjectMapper.ParseMailFile(textMail("Einladung zum Konzert"));

    assertThat(result.getName().trim()).isEqualTo("Einladung zum Konzert");
    assertThat(result.getKosten()).isZero();
    assertThat(result.getStart()).isAfterOrEqualTo(beforeParse);
    assertThat(result.getEnd()).isEqualTo(result.getStart().plusDays(1));
  }

  private EventService serviceWithRealObjectMapper() {
    return new EventService(
        eventRepository,
        tripRepository,
        ausgabeService,
        userRepository,
        new ObjectMapper(),
        userEventRepository);
  }

  private User userWithEvents(long id) {
    User user = new User();
    user.setId(id);
    user.setEvents(new ArrayList<>());
    return user;
  }

  private ByteArrayInputStream jsonLdMail(String json) {
    return mail("application/ld+json; charset=UTF-8", json);
  }

  private ByteArrayInputStream textMail(String text) {
    return mail("text/plain; charset=UTF-8", text);
  }

  private ByteArrayInputStream mail(String contentType, String body) {
    String rawMail =
        """
        Subject: Test Mail
        Content-Type: %s

        %s
        """
            .formatted(contentType, body);
    return new ByteArrayInputStream(rawMail.getBytes(StandardCharsets.UTF_8));
  }
}
