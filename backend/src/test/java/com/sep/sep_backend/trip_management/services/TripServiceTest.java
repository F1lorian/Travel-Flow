package com.sep.sep_backend.trip_management.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.util.ArrayList;
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
    User user = userWithTrips(7L);
    when(tripRepository.save(any(Trip.class))).thenReturn(trip);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    Trip result = tripService.createTrip("Paris", null, null, 7L);

    assertEquals("Paris", result.getName());
    assertTrue(user.getTrips().contains(trip));
    verify(tripRepository).save(any(Trip.class));
    verify(userRepository).save(user);
  }

  @Test
  void createTrip_throwsWhenNameIsBlank() {
    assertThrows(IllegalArgumentException.class, () -> tripService.createTrip("", null, null, 7L));
    assertThrows(
        IllegalArgumentException.class, () -> tripService.createTrip("   ", null, null, 7L));
  }

  @Test
  void createTrip_throwsWhenNameIsNull() {
    assertThrows(
        IllegalArgumentException.class, () -> tripService.createTrip(null, null, null, 7L));
  }

  @Test
  void getAllTrips_returnsAllTrips() {
    when(tripRepository.findAllByUserId(7L))
        .thenReturn(List.of(new Trip("Paris"), new Trip("Berlin")));

    List<Trip> result = tripService.getAllTrips(7L);

    assertEquals(2, result.size());
    verify(tripRepository).findAllByUserId(7L);
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

  private User userWithTrips(long id) {
    User user = new User();
    user.setId(id);
    user.setTrips(new ArrayList<>());
    return user;
  }
}
