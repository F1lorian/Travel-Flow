package com.sep.sep_backend.TripManagement.services;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private TripService tripService;

    @Test
    void createTrip_savesAndReturnsTrip() {
        Trip trip = new Trip("Paris");
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);

        Trip result = tripService.createTrip("Paris");

        assertEquals("Paris", result.getName());
        verify(tripRepository).save(any(Trip.class));
    }

    @Test
    void createTrip_throwsWhenNameIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(""));
        assertThrows(IllegalArgumentException.class, () -> tripService.createTrip("   "));
    }

    @Test
    void createTrip_throwsWhenNameIsNull() {
        assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(null));
    }

    @Test
    void getAllTrips_returnsAllTrips() {
        when(tripRepository.findAll()).thenReturn(List.of(new Trip("Paris"), new Trip("Berlin")));

        List<Trip> result = tripService.getAllTrips();

        assertEquals(2, result.size());
    }

    @Test
    void getTrip_returnsTrip_whenExists() {
        Trip trip = new Trip("Paris");
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        Optional<Trip> result = tripService.getTrip(1L);

        assertTrue(result.isPresent());
        assertEquals("Paris", result.get().getName());
    }

    @Test
    void getTrip_returnsEmpty_whenNotFound() {
        when(tripRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Trip> result = tripService.getTrip(99L);

        assertTrue(result.isEmpty());
    }
}