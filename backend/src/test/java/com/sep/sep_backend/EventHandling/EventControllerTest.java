package com.sep.sep_backend.EventHandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        List<EventCalendarDTO> events = List.of(
                new EventCalendarDTO("42", "Museum", 20, LocalDateTime.of(2026, 5, 7, 14, 30), LocalDateTime.of(2026, 5, 8, 14, 30), 1)
        );

    when(eventService.GetAllEventsFromDb()).thenReturn(events);

    ResponseEntity<List<EventCalendarDTO>> response = eventController.getAllEvents();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isEqualTo(events);
    verify(eventService).GetAllEventsFromDb();
  }
}
