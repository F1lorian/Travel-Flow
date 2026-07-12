package com.sep.sep_backend.tripManagement.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.sep_backend.accountmanagement.CurrentUserService;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.services.TripService;
import java.util.List;
import java.util.Optional;
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
    value = TripController.class,
    excludeAutoConfiguration = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class
    })
class TripControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TripService tripService;

  @MockitoBean private UserTripRepository userTripRepository;

  @MockitoBean private CurrentUserService currentUserService;

  private final Authentication authentication = authentication("testuser");

  @BeforeEach
  void resolveCurrentUser() {
    when(currentUserService.requireUser(any())).thenReturn(user(7L));
  }

  @Test
  void getAllTrips_returnsOkWithTripList() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    when(tripService.getAllTrips(7L)).thenReturn(List.of(trip));
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/trips").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Paris"));
  }

  @Test
  void getTrip_returnsOk_whenUserIsMember() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    when(tripService.getTripForMember(1L, 7L)).thenReturn(trip);
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/trips/1").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Paris"));
  }

  @Test
  void getTrip_returns404WithMessage_whenNotFound() throws Exception {
    when(tripService.getTripForMember(99L, 7L))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: 99"));

    mockMvc
        .perform(get("/api/trips/99").principal(authentication))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Trip not found: 99"));
  }

  @Test
  void getTrip_returns403WithMessage_whenUserIsNotAMember() throws Exception {
    when(tripService.getTripForMember(1L, 7L))
        .thenThrow(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip"));

    mockMvc
        .perform(get("/api/trips/1").principal(authentication))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("You are not a member of this trip"));
  }

  @Test
  void createTrip_returnsOkWithCreatedTrip() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    when(tripService.createTrip(eq("Paris"), isNull(), isNull(), eq(7L))).thenReturn(trip);

    mockMvc
        .perform(
            post("/api/trips")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(authentication)
                .content("{\"name\": \"Paris\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Paris"));
  }

  @Test
  void createTrip_returns400WithReason_whenInputInvalid() throws Exception {
    when(tripService.createTrip(eq(""), isNull(), isNull(), eq(7L)))
        .thenThrow(new IllegalArgumentException("Trip name cannot be empty"));

    mockMvc
        .perform(
            post("/api/trips")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(authentication)
                .content("{\"name\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Trip name cannot be empty"));
  }

  @Test
  void deleteTrip_returnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/trips/1").principal(authentication))
        .andExpect(status().isNoContent());

    verify(tripService).deleteTrip(1L, 7L);
  }

  @Test
  void deleteTrip_returns403WithMessage_whenUserIsNotAMember() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip"))
        .when(tripService)
        .deleteTrip(1L, 7L);

    mockMvc
        .perform(delete("/api/trips/1").principal(authentication))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("You are not a member of this trip"));
  }

  @Test
  void removeFriendFromTripReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/trips/1/friends/8").principal(authentication))
        .andExpect(status().isNoContent());

    verify(tripService).removeFriendFromTrip(1L, 8L, 7L);
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
