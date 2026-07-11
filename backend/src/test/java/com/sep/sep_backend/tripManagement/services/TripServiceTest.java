package com.sep.sep_backend.tripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.entities.UserTripId;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  @Mock private TripRepository tripRepository;

  @Mock private EventRepository eventRepository;

  @Mock private UserRepository userRepository;

  @Mock private UserTripRepository userTripRepository;

  @Mock private UserEventRepository userEventRepository;

  @Mock private AusgabeRepository ausgabeRepository;

  @InjectMocks private TripService tripService;

  @Test
  void createTrip_savesAndReturnsTrip() {
    Trip trip = new Trip("Paris");
    User user = userWithTrips(7L);
    when(tripRepository.save(any(Trip.class))).thenReturn(trip);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    Trip result = tripService.createTrip("Paris", null, null, 7L);

    assertEquals("Paris", result.getName());
    verify(tripRepository).save(any(Trip.class));
    verify(userTripRepository).save(any(UserTrip.class));
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

  @Test
  void deleteTrip_deletesRelatedDataBeforeTrip() {
    when(tripRepository.existsById(1L)).thenReturn(true);

    tripService.deleteTrip(1L);

    InOrder inOrder =
        inOrder(
            userEventRepository,
            eventRepository,
            ausgabeRepository,
            userTripRepository,
            tripRepository);
    inOrder.verify(userEventRepository).deleteByEvent_Trip_Id(1L);
    inOrder.verify(eventRepository).deleteByTrip_Id(1L);
    inOrder.verify(ausgabeRepository).deleteByTrip_Id(1L);
    inOrder.verify(userTripRepository).deleteByIdTripId(1L);
    inOrder.verify(tripRepository).deleteById(1L);
  }

  @Test
  void deleteTrip_throwsWhenTripNotFound() {
    when(tripRepository.existsById(99L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> tripService.deleteTrip(99L));

    verify(userEventRepository, never()).deleteByEvent_Trip_Id(any());
    verify(eventRepository, never()).deleteByTrip_Id(any());
    verify(ausgabeRepository, never()).deleteByTrip_Id(any());
    verify(userTripRepository, never()).deleteByIdTripId(any());
    verify(tripRepository, never()).deleteById(any());
  }

  @Test
  void removeFriendFromTripDeletesFriendFromTripAndTripEvents() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User requester = userWithTrips(7L);
    User friend = userWithTrips(8L);
    UserTrip requesterTrip =
        new UserTrip(requester, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip friendTrip = new UserTrip(friend, trip, requester, UserTrip.InviteStatus.ACCEPTED);

    when(tripRepository.existsById(1L)).thenReturn(true);
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L))
        .thenReturn(Optional.of(requesterTrip));
    when(userTripRepository.findByIdUserIdAndIdTripId(8L, 1L))
        .thenReturn(Optional.of(friendTrip));

    tripService.removeFriendFromTrip(1L, 8L, 7L);

    InOrder inOrder = inOrder(userEventRepository, userTripRepository);
    inOrder.verify(userEventRepository).deleteByEvent_Trip_IdAndUser_Id(1L, 8L);
    inOrder.verify(userTripRepository).deleteById(new UserTripId(8L, 1L));
  }

  @Test
  void removeFriendFromTripRejectsWhenRequesterIsNotAcceptedTripMember() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User requester = userWithTrips(7L);
    UserTrip requesterTrip = new UserTrip(requester, trip, null, UserTrip.InviteStatus.PENDING);

    when(tripRepository.existsById(1L)).thenReturn(true);
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L))
        .thenReturn(Optional.of(requesterTrip));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> tripService.removeFriendFromTrip(1L, 8L, 7L));

    assertEquals("Only accepted trip members can remove friends", exception.getMessage());
    verify(userEventRepository, never()).deleteByEvent_Trip_IdAndUser_Id(any(), any());
    verify(userTripRepository, never()).deleteById(any());
  }

  private User userWithTrips(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }
}
