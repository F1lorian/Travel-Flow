package com.sep.sep_backend.tripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TripEventServiceTest {

  private static final long USER_ID = 7L;

  @Mock private EventRepository eventRepository;

  @Mock private TripService tripService;

  @Mock private CategoryService categoryService;

  @Mock private UserEventRepository userEventRepository;

  @Mock private UserRepository userRepository;

  @Mock private EmailNotificationService emailNotificationService;

  @InjectMocks private TripEventService eventService;

  private final LocalDateTime sampleStartTime = LocalDateTime.of(2026, 7, 15, 19, 30);
  private final LocalDateTime sampleEndTime = LocalDateTime.of(2026, 7, 16, 19, 30);

  @Test
  void addEventToTrip_savesAndReturnsEvent() {
    Trip trip = new Trip("Paris");
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    when(tripService.getTripForMember(1L, USER_ID)).thenReturn(trip);
    when(eventRepository.save(any(Event.class))).thenReturn(event);
    User user = new User();
    user.setId(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    Event result =
        eventService.addEventToTrip(
            1L, USER_ID, "Dinner", sampleStartTime, sampleEndTime, 50, null);

    assertEquals("Dinner", result.getName());
    assertEquals(50, result.getKosten());
    verify(eventRepository).save(any(Event.class));
  }

  @Test
  void addEventToTrip_makesTheCreatorOwnerAndAcceptedParticipant() {
    Trip trip = new Trip("Paris");
    when(tripService.getTripForMember(1L, USER_ID)).thenReturn(trip);
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    User user = new User();
    user.setId(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    Event result =
        eventService.addEventToTrip(
            1L, USER_ID, "Dinner", sampleStartTime, sampleEndTime, 50, null);

    // The calendar only lists events with an accepted participation, so the creator must get one.
    assertSame(user, result.getOwner());
    ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository).save(captor.capture());
    assertSame(result, captor.getValue().getEvent());
    assertSame(user, captor.getValue().getUser());
    assertEquals(UserEvent.InviteStatus.ACCEPTED, captor.getValue().getStatus());
  }

  @Test
  void addEventToTrip_emailsTheUserAboutTheNewEvent() {
    Trip trip = new Trip("Paris");
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    when(tripService.getTripForMember(1L, USER_ID)).thenReturn(trip);
    when(eventRepository.save(any(Event.class))).thenReturn(event);
    User user = new User();
    user.setId(USER_ID);
    user.setEmail("max@example.com");
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    eventService.addEventToTrip(1L, USER_ID, "Dinner", sampleStartTime, sampleEndTime, 50, null);

    // Every in-app notification is mirrored to the user's inbox.
    verify(emailNotificationService)
        .sendBestEffort(eq("max@example.com"), contains("Dinner"), contains("Paris"));
  }

  @Test
  void addEventToTrip_throwsWhenTripNotFound() {
    when(tripService.getTripForMember(99L, USER_ID))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: 99"));

    assertThrows(
        ResponseStatusException.class,
        () ->
            eventService.addEventToTrip(
                99L, USER_ID, "Dinner", sampleStartTime, sampleEndTime, 50, null));
  }

  @Test
  void addEventToTrip_throwsWhenNameIsBlank() {
    // Validation runs before the trip lookup, so no stubbing is needed
    assertThrows(
        IllegalArgumentException.class,
        () ->
            eventService.addEventToTrip(1L, USER_ID, "", sampleStartTime, sampleEndTime, 50, null));
  }

  @Test
  void getEventsForTrip_returnsEventList_forMembers() {
    Trip trip = new Trip("Paris");
    List<Event> events = List.of(new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip));
    when(tripService.getTripForMember(1L, USER_ID)).thenReturn(trip);
    when(eventRepository.findByTrip_Id(1L)).thenReturn(events);

    List<Event> result = eventService.getEventsForTrip(1L, USER_ID);

    assertEquals(1, result.size());
    assertEquals("Dinner", result.get(0).getName());
  }

  @Test
  void getEventsForTrip_throws403_whenUserIsNotAMember() {
    when(tripService.getTripForMember(1L, USER_ID))
        .thenThrow(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip"));

    assertThrows(ResponseStatusException.class, () -> eventService.getEventsForTrip(1L, USER_ID));
    verify(eventRepository, never()).findByTrip_Id(any());
  }

  @Test
  void deleteEvent_deletesUserEventLinksBeforeEvent() {
    Trip trip = tripWithId(1L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));

    eventService.deleteEvent(42L, USER_ID);

    verify(tripService).requireMember(1L, USER_ID);
    InOrder inOrder = inOrder(userEventRepository, eventRepository);
    inOrder.verify(userEventRepository).deleteByEvent_Id(42L);
    inOrder.verify(eventRepository).delete(event);
  }

  @Test
  void deleteEvent_throwsWhenEventNotFound() {
    when(eventRepository.findById(42L)).thenReturn(Optional.empty());

    assertThrows(ResponseStatusException.class, () -> eventService.deleteEvent(42L, USER_ID));

    verify(userEventRepository, never()).deleteByEvent_Id(any());
    verify(eventRepository, never()).delete(any(Event.class));
  }

  @Test
  void deleteEvent_throws403_whenUserIsNotAMemberOfTheEventsTrip() {
    Trip trip = tripWithId(1L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip"))
        .when(tripService)
        .requireMember(1L, USER_ID);

    assertThrows(ResponseStatusException.class, () -> eventService.deleteEvent(42L, USER_ID));

    verify(userEventRepository, never()).deleteByEvent_Id(any());
    verify(eventRepository, never()).delete(any(Event.class));
  }

  @Test
  void moveEventToTrip_movesEventBetweenTrips() {
    Trip source = tripWithId(1L);
    Trip target = tripWithId(2L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, source);
    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));
    when(tripService.getTripForMember(2L, USER_ID)).thenReturn(target);
    when(eventRepository.save(event)).thenReturn(event);

    Event result = eventService.moveEventToTrip(42L, 2L, USER_ID);

    assertEquals(target, result.getTrip());
    verify(tripService).requireMember(1L, USER_ID);
  }

  @Test
  void moveEventToTrip_rejectsMoveToSameTrip() {
    Trip trip = tripWithId(1L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));
    when(tripService.getTripForMember(1L, USER_ID)).thenReturn(trip);

    assertThrows(
        IllegalArgumentException.class, () -> eventService.moveEventToTrip(42L, 1L, USER_ID));
    verify(eventRepository, never()).save(any());
  }

  private Trip tripWithId(long id) {
    Trip trip = new Trip("Trip " + id);
    trip.setId(id);
    return trip;
  }
}
