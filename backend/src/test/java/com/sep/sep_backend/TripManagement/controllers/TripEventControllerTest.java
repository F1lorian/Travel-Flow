package com.sep.sep_backend.TripManagement.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.TripManagement.services.TripEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TripEventController.class)
class TripEventControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TripEventService eventService;

  private final LocalDateTime sampleDateTime = LocalDateTime.of(2026, 7, 15, 19, 30, 0);

  @Test
  void getEvents_returnsOkWithEventList() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    Event event = new Event("Dinner", sampleDateTime, 50, trip);
    event.setId(1L);
    when(eventService.getEventsForTrip(1L)).thenReturn(List.of(event));

    mockMvc
        .perform(get("/api/trips/1/events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Dinner"))
        .andExpect(jsonPath("$[0].kosten").value(50));
  }

  @Test
  void addEvent_returnsOkWithCreatedEvent() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    Event event = new Event("Dinner", sampleDateTime, 50, trip);
    event.setId(1L);
    when(eventService.addEventToTrip(eq(1L), eq("Dinner"), eq(sampleDateTime), eq(50)))
        .thenReturn(event);

    mockMvc
        .perform(
            post("/api/trips/1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Dinner\", \"dateTime\": \"2026-07-15T19:30:00\", \"kosten\": 50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Dinner"))
        .andExpect(jsonPath("$.kosten").value(50));
  }
}
