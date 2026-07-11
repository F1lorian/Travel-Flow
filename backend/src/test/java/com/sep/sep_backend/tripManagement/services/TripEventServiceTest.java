package com.sep.sep_backend.tripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserTripRepository;
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

@ExtendWith(MockitoExtension.class)
class TripEventServiceTest {

  @Mock private EventRepository eventRepository;

  @Mock private TripRepository tripRepository;

  @Mock private CategoryService categoryService;

  @Mock private UserEventRepository userEventRepository;

  @Mock private UserTripRepository userTripRepository;

  @InjectMocks private TripEventService eventService;

  private final LocalDateTime sampleStartTime = LocalDateTime.of(2026, 7, 15, 19, 30);
  private final LocalDateTime sampleEndTime = LocalDateTime.of(2026, 7, 16, 19, 30);

  @Test
  void addEventToTrip_savesAndReturnsEvent() {
    Trip trip = new Trip("Paris");
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(eventRepository.save(any(Event.class))).thenReturn(event);
    when(userTripRepository.findByIdTripId(1L)).thenReturn(List.of());

    Event result =
        eventService.addEventToTrip(1L, "Dinner", sampleStartTime, sampleEndTime, 50, null);

    assertEquals("Dinner", result.getName());
    assertEquals(50, result.getKosten());
    verify(eventRepository).save(any(Event.class));
  }

  @Test
  void addEventToTripCreatesUserEventsForAcceptedAndPendingTripMembers() {
    Trip trip = new Trip("Paris");
    trip.setId(1L);
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
    event.setId(42L);
    User owner = user(7L);
    User acceptedFriend = user(8L);
    User pendingFriend = user(9L);
    User declinedFriend = user(10L);
    UserTrip ownerTrip = new UserTrip(owner, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip acceptedTrip =
        new UserTrip(acceptedFriend, trip, owner, UserTrip.InviteStatus.ACCEPTED);
    UserTrip pendingTrip = new UserTrip(pendingFriend, trip, owner, UserTrip.InviteStatus.PENDING);
    UserTrip declinedTrip =
        new UserTrip(declinedFriend, trip, owner, UserTrip.InviteStatus.DECLINED);

    when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
    when(eventRepository.save(any(Event.class))).thenReturn(event);
    when(userTripRepository.findByIdTripId(1L))
        .thenReturn(List.of(ownerTrip, acceptedTrip, pendingTrip, declinedTrip));
    when(userEventRepository.existsByEvent_IdAndUser_Id(42L, 7L)).thenReturn(false);
    when(userEventRepository.existsByEvent_IdAndUser_Id(42L, 8L)).thenReturn(false);
    when(userEventRepository.existsByEvent_IdAndUser_Id(42L, 9L)).thenReturn(false);

    eventService.addEventToTrip(1L, "Dinner", sampleStartTime, sampleEndTime, 50, null);

    ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository, times(3)).save(captor.capture());
    assertEquals(owner, captor.getAllValues().get(0).getUser());
    assertEquals(UserEvent.InviteStatus.ACCEPTED, captor.getAllValues().get(0).getStatus());
    assertEquals(acceptedFriend, captor.getAllValues().get(1).getUser());
    assertEquals(UserEvent.InviteStatus.ACCEPTED, captor.getAllValues().get(1).getStatus());
    assertEquals(pendingFriend, captor.getAllValues().get(2).getUser());
    assertEquals(UserEvent.InviteStatus.PENDING, captor.getAllValues().get(2).getStatus());
  }

  @Test
  void addEventToTrip_throwsWhenTripNotFound() {
    when(tripRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> eventService.addEventToTrip(99L, "Dinner", sampleStartTime, sampleEndTime, 50, null));
  }

  @Test
  void addEventToTrip_throwsWhenNameIsBlank() {
    // Validation runs before the trip lookup, so no repository stubbing is needed
    assertThrows(
        IllegalArgumentException.class,
        () -> eventService.addEventToTrip(1L, "", sampleStartTime, sampleEndTime, 50, null));
  }

  @Test
  void getEventsForTrip_returnsEventList() {
    Trip trip = new Trip("Paris");
    List<Event> events = List.of(new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip));
    when(eventRepository.findByTrip_Id(1L)).thenReturn(events);

    List<Event> result = eventService.getEventsForTrip(1L);

    assertEquals(1, result.size());
    assertEquals("Dinner", result.get(0).getName());
  }

  @Test
  void deleteEvent_deletesUserEventLinksBeforeEvent() {
    Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, new Trip("Paris"));
    when(eventRepository.findById(42L)).thenReturn(Optional.of(event));

    eventService.deleteEvent(42L);

    InOrder inOrder = inOrder(userEventRepository, eventRepository);
    inOrder.verify(userEventRepository).deleteByEvent_Id(42L);
    inOrder.verify(eventRepository).delete(event);
  }

  @Test
  void deleteEvent_throwsWhenEventNotFound() {
    when(eventRepository.findById(42L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> eventService.deleteEvent(42L));

    verify(userEventRepository, never()).deleteByEvent_Id(any());
    verify(eventRepository, never()).delete(any(Event.class));
  }

  private User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }
}
