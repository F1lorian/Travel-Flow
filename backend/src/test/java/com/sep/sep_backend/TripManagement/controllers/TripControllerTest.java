package com.sep.sep_backend.TripManagement.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.TripManagement.services.TripService;
import com.sep.sep_backend.repos.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = TripController.class,
    excludeAutoConfiguration = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class
    })
class TripControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TripService tripService;

  @MockitoBean private UserRepository userRepository;

  @Test
  void getAllTrips_returnsOkWithTripList() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    Authentication authentication = authentication("testuser");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user(7L)));
    when(tripService.getAllTrips(7L)).thenReturn(List.of(trip));

    mockMvc
        .perform(get("/api/trips").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Paris"));
  }

  @Test
  void getTrip_returnsOk_whenFound() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    when(tripService.getTrip(1L)).thenReturn(Optional.of(trip));

    mockMvc
        .perform(get("/api/trips/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Paris"));
  }

  @Test
  void getTrip_returns404_whenNotFound() throws Exception {
    when(tripService.getTrip(99L)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/trips/99")).andExpect(status().isNotFound());
  }

  @Test
  void createTrip_returnsOkWithCreatedTrip() throws Exception {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    Authentication authentication = authentication("testuser");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", ""))
        .thenReturn(Optional.of(user(7L)));
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
