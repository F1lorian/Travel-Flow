package com.sep.sep_backend.tripManagement.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.sep_backend.accountmanagement.CurrentUserService;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.tripManagement.services.TripEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(
    value = TripEventController.class,
    excludeAutoConfiguration = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class
    })
class TripEventControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TripEventService eventService;

  @MockitoBean private CurrentUserService currentUserService;

  private final Authentication authentication = authentication("testuser");

  private final LocalDateTime sampleStartTime = LocalDateTime.of(2026, 7, 15, 19, 30, 0);
  private final LocalDateTime sampleEndTime = LocalDateTime.of(2026, 7, 16, 19, 30, 0);

  @BeforeEach
  void resolveCurrentUser() {
    User user = new User();
    user.setId(7L);
    when(currentUserService.requireUser(any())).thenReturn(user);
  }

  @Test
  void getEvents_returnsOkWithEventList() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    event.setId(1L);
    when(eventService.getEventsForTrip(1L, 7L)).thenReturn(List.of(event));

    mockMvc
        .perform(get("/api/trips/1/events").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Dinner"))
        .andExpect(jsonPath("$[0].kosten").value(50));
  }

  @Test
  void getEvents_returns403WithMessage_whenUserIsNotAMember() throws Exception {
    when(eventService.getEventsForTrip(1L, 7L))
        .thenThrow(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip"));

    mockMvc
        .perform(get("/api/trips/1/events").principal(authentication))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("You are not a member of this trip"));
  }

  @Test
  void addEvent_returnsOkWithCreatedEvent() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);

    LocalDateTime startTime = LocalDateTime.of(2026, 7, 15, 19, 30, 0);
    LocalDateTime endTime = LocalDateTime.of(2026, 7, 15, 20, 30, 0);

    Event event = new Event("Dinner", startTime, endTime, 50, trip);
    event.setId(1L);

    when(eventService.addEventToTrip(
            eq(1L), eq(7L), eq("Dinner"), eq(startTime), eq(endTime), eq(50), isNull()))
        .thenReturn(event);

    mockMvc
        .perform(
            post("/api/trips/1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(authentication)
                .content(
                    "{\"name\": \"Dinner\", \"startTime\": \"2026-07-15T19:30:00\","
                        + " \"endTime\": \"2026-07-15T20:30:00\", \"kosten\": 50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Dinner"))
        .andExpect(jsonPath("$.kosten").value(50));
  }

  @Test
  void addEvent_returns400WithReason_whenNameIsBlank() throws Exception {
    when(eventService.addEventToTrip(eq(1L), eq(7L), eq(""), isNull(), isNull(), eq(0), isNull()))
        .thenThrow(new IllegalArgumentException("Event name cannot be empty"));

    mockMvc
        .perform(
            post("/api/trips/1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(authentication)
                .content("{\"name\": \"\", \"kosten\": 0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Event name cannot be empty"));
  }

  @Test
  void deleteEvent_returnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/trips/1/events/42").principal(authentication))
        .andExpect(status().isNoContent());

    verify(eventService).deleteEvent(42L, 7L);
  }

  @Test
  void moveEvent_returnsOkWithMovedEvent() throws Exception {
    Trip target = new Trip("Berlin");
    target.setId(2L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, target);
    event.setId(42L);
    when(eventService.moveEventToTrip(42L, 2L, 7L)).thenReturn(event);

    mockMvc
        .perform(put("/api/trips/1/events/42/move/2").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Dinner"));
  }

  private Authentication authentication(String username) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(username);
    return authentication;
  }
}
