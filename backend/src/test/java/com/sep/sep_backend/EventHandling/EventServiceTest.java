package com.sep.sep_backend.EventHandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  @Mock private EventRepository eventRepository;

  @Mock private TripRepository tripRepository;

  @Mock private AusgabeService ausgabeService;

  @InjectMocks private EventService eventService;

  @Test
  void saveEventToDBSpeichertEventFuerVorhandeneTripId() throws Exception {
    Trip trip = new Trip("London");
    EventRequestDTO request = new EventRequestDTO("Museum", 25, 1L);
    LocalDateTime beforeSave = LocalDateTime.now();

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    saveEventToDB(request);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());

    Event savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getName()).isEqualTo("Museum");
    assertThat(savedEvent.getKosten()).isEqualTo(25);
    assertThat(savedEvent.getTrip()).isSameAs(trip);
    assertThat(savedEvent.getStartTime()).isAfterOrEqualTo(beforeSave);
  }

  @Test
  void saveEventToDBWirftExceptionWennTripNichtExistiert() {
    EventRequestDTO request = new EventRequestDTO("Museum", 25, 99L);
    when(tripRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> saveEventToDB(request))
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(RuntimeException.class)
        .hasRootCauseMessage("Zu dieser trip Id findet sich kein eintrag in der Datenbank");

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

    EventResponseDTO result = eventService.SaveEventResponseToDB(responseDTO, 1L);

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
  }

  @Test
  void getAllEventsFromDbWandeltEventsInCalendarDTOsUm() {
    Trip trip = new Trip("London");
    LocalDateTime start = LocalDateTime.of(2026, 5, 7, 14, 30);
    LocalDateTime end = LocalDateTime.of(2026, 5, 8, 14, 30);
    Event event = new Event("Museum", start, end, 25, trip);
    event.setId(42L);

    when(eventRepository.findAll()).thenReturn(List.of(event));

    List<EventCalendarDTO> result = eventService.GetAllEventsFromDb();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo("42");
    assertThat(result.getFirst().getTitle()).isEqualTo("Museum");
    assertThat(result.getFirst().getStart()).isEqualTo(start);
    verify(eventRepository).findAll();
  }

  private void saveEventToDB(EventRequestDTO request)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method = EventService.class.getDeclaredMethod("SaveEventToDB", EventRequestDTO.class);
    method.invoke(eventService, request);
  }
}
