package com.sep.sep_backend.tripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.entities.UserTripId;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  @Mock private TripRepository tripRepository;

  @Mock private EventRepository eventRepository;

  @Mock private UserRepository userRepository;

  @Mock private UserTripRepository userTripRepository;

  @Mock private UserEventRepository userEventRepository;

  @Mock private AusgabeRepository ausgabeRepository;

  @Mock private EmailNotificationService emailNotificationService;

  @InjectMocks private TripService tripService;

  @Test
  void createTrip_savesAndReturnsTrip() {
    Trip trip = new Trip("Paris");
    User user = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(tripRepository.save(any(Trip.class))).thenReturn(trip);

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
  void createTrip_throwsWhenEndDateBeforeStartDate() {
    LocalDate start = LocalDate.of(2026, 8, 10);
    LocalDate end = LocalDate.of(2026, 8, 1);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> tripService.createTrip("Paris", start, end, 7L));

    assertEquals("Trip end date cannot be before its start date", exception.getMessage());
    verify(tripRepository, never()).save(any());
  }

  @Test
  void createTrip_throwsWhenUserUnknown_withoutSavingATrip() {
    when(userRepository.findById(7L)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class, () -> tripService.createTrip("Paris", null, null, 7L));

    // The creator is resolved before saving, so a bad user id never leaves an orphaned trip row.
    verify(tripRepository, never()).save(any());
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
  void getTripForMember_returnsTrip_whenUserIsMember() {
    Trip trip = new Trip("Paris");
    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L))
        .thenReturn(Optional.of(acceptedMembership()));

    Trip result = tripService.getTripForMember(1L, 7L);

    assertEquals("Paris", result.getName());
  }

  @Test
  void getTripForMember_throws404_whenTripUnknown() {
    when(tripRepository.findById(99L)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.getTripForMember(99L, 7L));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void getTripForMember_throws403_whenUserIsNotAMember() {
    when(tripRepository.findById(1L)).thenReturn(Optional.of(new Trip("Paris")));
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.getTripForMember(1L, 7L));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
  }

  @Test
  void deleteTrip_deletesRelatedDataBeforeTrip() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User owner = userWithTrips(7L);
    trip.setOwner(owner);
    when(tripRepository.findById(1L)).thenReturn(Optional.of(new Trip("Paris")));
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L))
        .thenReturn(Optional.of(acceptedMembership()));

    tripService.deleteTrip(1L, 7L);

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
    when(tripRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> tripService.deleteTrip(99L, 7L));

    verify(userEventRepository, never()).deleteByEvent_Trip_Id(any());
    verify(eventRepository, never()).deleteByTrip_Id(any());
    verify(ausgabeRepository, never()).deleteByTrip_Id(any());
    verify(userTripRepository, never()).deleteByIdTripId(any());
    verify(tripRepository, never()).deleteById(any());
  }

  @Test
  void deleteTrip_rejectsWhenRequesterIsNotOwner() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User owner = userWithTrips(7L);
    trip.setOwner(owner);

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.deleteTrip(1L, 8L));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verify(userEventRepository, never()).deleteByEvent_Trip_Id(any());
    verify(eventRepository, never()).deleteByTrip_Id(any());
    verify(ausgabeRepository, never()).deleteByTrip_Id(any());
    verify(userTripRepository, never()).deleteByIdTripId(any());
    verify(tripRepository, never()).deleteById(any());
  }

  @Test
  void deleteTrip_emailsTheUserAboutTheDeletion() {
    when(tripRepository.findById(1L)).thenReturn(Optional.of(new Trip("Paris")));
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L))
        .thenReturn(Optional.of(acceptedMembership()));
    User user = userWithId(7L);
    user.setEmail("max@example.com");
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    tripService.deleteTrip(1L, 7L);

    verify(emailNotificationService)
        .sendBestEffort(eq("max@example.com"), contains("Paris"), contains("deleted"));
  }

  @Test
  void deleteTrip_throws403_whenUserIsNotAMember() {
    when(tripRepository.findById(1L)).thenReturn(Optional.of(new Trip("Paris")));
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.deleteTrip(1L, 7L));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verify(userEventRepository, never()).deleteByEvent_Trip_Id(any());
    verify(eventRepository, never()).deleteByTrip_Id(any());
    verify(ausgabeRepository, never()).deleteByTrip_Id(any());
    verify(userTripRepository, never()).deleteByIdTripId(any());
    verify(tripRepository, never()).deleteById(any());
  }

  @Test
  void removeFriendFromTripDeletesFriendFromTripAndTripEventsWhenRequesterIsOwner() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User owner = userWithId(7L);
    trip.setOwner(owner);
    User friend = userWithId(8L);
    UserTrip requesterTrip = new UserTrip(owner, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip friendTrip = new UserTrip(friend, trip, owner, UserTrip.InviteStatus.ACCEPTED);

    when(tripRepository.existsById(1L)).thenReturn(true);
    when(userTripRepository.findByIdUserIdAndIdTripId(7L, 1L))
        .thenReturn(Optional.of(requesterTrip));
    when(userTripRepository.findByIdUserIdAndIdTripId(8L, 1L)).thenReturn(Optional.of(friendTrip));

    tripService.removeFriendFromTrip(1L, 8L, 7L);

    InOrder inOrder = inOrder(userEventRepository, userTripRepository);
    inOrder.verify(userEventRepository).deleteByEvent_Trip_IdAndUser_Id(1L, 8L);
    inOrder.verify(userTripRepository).deleteById(new UserTripId(8L, 1L));
  }

  @Test
  void removeFriendFromTripAllowsUserToRemoveThemselves() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User owner = userWithId(7L);
    trip.setOwner(owner);
    User friend = userWithTrips(8L);
    UserTrip friendTrip = new UserTrip(friend, trip, owner, UserTrip.InviteStatus.ACCEPTED);

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByIdUserIdAndIdTripId(8L, 1L)).thenReturn(Optional.of(friendTrip));

    tripService.removeFriendFromTrip(1L, 8L, 8L);

    verify(userEventRepository).deleteByEvent_Trip_IdAndUser_Id(1L, 8L);
    verify(userTripRepository).deleteById(new UserTripId(8L, 1L));
  }

  @Test
  void removeFriendFromTripRejectsWhenRequesterIsNeitherOwnerNorSelf() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User owner = userWithTrips(7L);
    trip.setOwner(owner);

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> tripService.removeFriendFromTrip(1L, 8L, 9L));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verify(userEventRepository, never()).deleteByEvent_Trip_IdAndUser_Id(any(), any());
    verify(userTripRepository, never()).deleteById(any());
  }

  @Test
  void removeFriendFromTripThrowsWhenFriendNotPartOfTrip() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    User owner = userWithTrips(7L);
    trip.setOwner(owner);

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByIdUserIdAndIdTripId(8L, 1L)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class, () -> tripService.removeFriendFromTrip(1L, 8L, 7L));
  }

  private User userWithTrips(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private User userWithId(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private UserTrip acceptedMembership() {
    UserTrip membership = new UserTrip();
    membership.setStatus(UserTrip.InviteStatus.ACCEPTED);
    return membership;
  }
}
