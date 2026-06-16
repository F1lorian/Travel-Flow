package com.sep.sep_backend.EventHandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
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
        EventController.class.getDeclaredMethod("EventsManuellErstellen", EventRequestDTO.class);

    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    assertThat(postMapping.value()).containsExactly("event/create/manual");
  }

  @Test
  void eventControllerHatDenAllEventsEndpoint() throws NoSuchMethodException {
    Method method = EventController.class.getDeclaredMethod("getAllEvents");

    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    assertThat(getMapping.value()).containsExactly("event/pull/all_events");
  }

  @Test
  void getAllEventsGibtCalendarDtosAusServiceZurueck() {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);
    List<EventCalendarDTO> events =
        List.of(
            new EventCalendarDTO(
                "42",
                "Museum",
                20,
                LocalDateTime.of(2026, 5, 7, 14, 30),
                LocalDateTime.of(2026, 5, 8, 14, 30),
                1L));

    when(eventService.GetAllEventsFromDb()).thenReturn(events);

    ResponseEntity<List<EventCalendarDTO>> response = eventController.getAllEvents();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isEqualTo(events);
    verify(eventService).GetAllEventsFromDb();
  }

  @Test
  void eventsManuellErstellenGibtServiceResponseZurueck() {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);
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

    when(eventService.SaveEventToDB(request)).thenReturn(created);

    ResponseEntity<EventResponseDTO> response = eventController.EventsManuellErstellen(request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isSameAs(created);
    verify(eventService).SaveEventToDB(request);
  }

  @Test
  void parseEmailErlaubtImportOhneTripId() throws NoSuchMethodException {
    Method method =
        EventController.class.getDeclaredMethod("parseEmail", MultipartFile.class, Long.class);

    RequestParam tripIdParam = method.getParameters()[1].getAnnotation(RequestParam.class);

    assertThat(tripIdParam.value()).isEqualTo("tripId");
    assertThat(tripIdParam.required()).isFalse();
  }

  @Test
  void parseEmailGibtGeparstesEventZurueckOhneEsZuSpeichern() throws Exception {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);
    MultipartFile file = mock(MultipartFile.class);
    InputStream inputStream = new ByteArrayInputStream("mail".getBytes());
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);

    when(file.getInputStream()).thenReturn(inputStream);
    when(eventService.ParseMailFile(inputStream)).thenReturn(parsedEvent);

    EventResponseDTO response = eventController.parseEmail(file, null);

    assertThat(response).isSameAs(parsedEvent);
    verify(eventService).ParseMailFile(inputStream);
  }

  @Test
  void saveImportedEventSpeichertImportOhneTripId() {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);
    EventResponseDTO savedEvent = new EventResponseDTO("1", "Konzert", 49, null, null, null);

    when(eventService.SaveEventResponseToDB(parsedEvent, null)).thenReturn(savedEvent);

    EventResponseDTO response = eventController.saveImportedEvent(parsedEvent, null);

    assertThat(response).isSameAs(savedEvent);
    verify(eventService).SaveEventResponseToDB(parsedEvent, null);
  }

  @Test
  void saveImportedEventSpeichertImportMitTripId() {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);
    EventResponseDTO parsedEvent = new EventResponseDTO("Konzert", 49, null, null);
    EventResponseDTO savedEvent = new EventResponseDTO("1", "Konzert", 49, null, null, 7L);

    when(eventService.SaveEventResponseToDB(parsedEvent, 7L)).thenReturn(savedEvent);

    EventResponseDTO response = eventController.saveImportedEvent(parsedEvent, 7L);

    assertThat(response).isSameAs(savedEvent);
    verify(eventService).SaveEventResponseToDB(parsedEvent, 7L);
  }

  @Test
  void deleteEventFromDBGibtBestaetigungZurueck() {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);

    ResponseEntity<DeleteEventResponseDTO> response = eventController.DeleteEventFromDB(42L);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody())
        .isEqualTo(new DeleteEventResponseDTO("Event erfolgreich gelöscht"));
    verify(eventService).DeleteEventFromDB(42L);
  }

  @Test
  void editEventGibtServiceResponseZurueck() {
    EventService eventService = mock(EventService.class);
    EventController eventController = new EventController(eventService);
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
}
