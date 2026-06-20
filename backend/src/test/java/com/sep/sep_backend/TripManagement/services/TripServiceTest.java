package com.sep.sep_backend.TripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  @Mock private TripRepository tripRepository;
  @Mock private EventRepository eventRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private TripService tripService;

  @Test
  void createTrip_savesAndReturnsTrip() {
    Trip trip = new Trip("Paris");
    when(userRepository.findByName("alice")).thenReturn(Optional.of(new User()));
    when(tripRepository.save(any(Trip.class))).thenReturn(trip);

    Trip result = tripService.createTrip("Paris", null, null, "alice");

    assertEquals("Paris", result.getName());
    verify(tripRepository).save(any(Trip.class));
  }

  @Test
  void createTrip_throwsWhenNameIsBlank() {
    assertThrows(
        IllegalArgumentException.class, () -> tripService.createTrip("", null, null, "alice"));
    assertThrows(
        IllegalArgumentException.class, () -> tripService.createTrip("   ", null, null, "alice"));
  }

  @Test
  void createTrip_throwsWhenNameIsNull() {
    assertThrows(
        IllegalArgumentException.class, () -> tripService.createTrip(null, null, null, "alice"));
  }

  @Test
  void getAllTripsForUser_returnsUsersTrips() {
    when(tripRepository.findTripsByUsername("alice"))
        .thenReturn(List.of(new Trip("Paris"), new Trip("Berlin")));

    List<Trip> result = tripService.getAllTripsForUser("alice");

    assertEquals(2, result.size());
  }

  @Test
  void getTrip_returnsTrip_whenOwnedAndExists() {
    Trip trip = new Trip("Paris");
    when(tripRepository.countByUsernameAndTripId("alice", 1L)).thenReturn(1L);
    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

    Optional<Trip> result = tripService.getTrip(1L, "alice");

    assertTrue(result.isPresent());
    assertEquals("Paris", result.get().getName());
  }

  @Test
  void getTrip_returnsEmpty_whenNotOwned() {
    when(tripRepository.countByUsernameAndTripId("alice", 99L)).thenReturn(0L);

    Optional<Trip> result = tripService.getTrip(99L, "alice");

    assertTrue(result.isEmpty());
  }
}
