package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.TripManagement.services.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TripController.class)
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripService tripService;

    @Test
    void getAllTrips_returnsOkWithTripList() throws Exception {
        Trip trip = new Trip("Paris");
        trip.setId(1L);
        when(tripService.getAllTrips()).thenReturn(List.of(trip));

        mockMvc.perform(get("/api/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Paris"));
    }

    @Test
    void getTrip_returnsOk_whenFound() throws Exception {
        Trip trip = new Trip("Paris");
        trip.setId(1L);
        when(tripService.getTrip(1L)).thenReturn(Optional.of(trip));

        mockMvc.perform(get("/api/trips/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paris"));
    }

    @Test
    void getTrip_returns404_whenNotFound() throws Exception {
        when(tripService.getTrip(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/trips/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTrip_returnsOkWithCreatedTrip() throws Exception {
        Trip trip = new Trip("Paris");
        trip.setId(1L);
        when(tripService.createTrip("Paris")).thenReturn(trip);

        mockMvc.perform(post("/api/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Paris\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paris"));
    }
}