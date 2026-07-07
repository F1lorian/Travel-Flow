package com.sep.sep_backend.tripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
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
    when(userTripRepository.existsByIdUserIdAndIdTripId(7L, 1L)).thenReturn(true);

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
    when(userTripRepository.existsByIdUserIdAndIdTripId(7L, 1L)).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.getTripForMember(1L, 7L));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
  }

  @Test
  void deleteTrip_deletesRelatedDataBeforeTrip() {
    when(tripRepository.findById(1L)).thenReturn(Optional.of(new Trip("Paris")));
    when(userTripRepository.existsByIdUserIdAndIdTripId(7L, 1L)).thenReturn(true);

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

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.deleteTrip(99L, 7L));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    verify(tripRepository, never()).deleteById(any());
  }

  @Test
  void deleteTrip_emailsTheUserAboutTheDeletion() {
    when(tripRepository.findById(1L)).thenReturn(Optional.of(new Trip("Paris")));
    when(userTripRepository.existsByIdUserIdAndIdTripId(7L, 1L)).thenReturn(true);
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
    when(userTripRepository.existsByIdUserIdAndIdTripId(7L, 1L)).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> tripService.deleteTrip(1L, 7L));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verify(userEventRepository, never()).deleteByEvent_Trip_Id(any());
    verify(eventRepository, never()).deleteByTrip_Id(any());
    verify(ausgabeRepository, never()).deleteByTrip_Id(any());
    verify(userTripRepository, never()).deleteByIdTripId(any());
    verify(tripRepository, never()).deleteById(any());
  }

  private User userWithId(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }
}
