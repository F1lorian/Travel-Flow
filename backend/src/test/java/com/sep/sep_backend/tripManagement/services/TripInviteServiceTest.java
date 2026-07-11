package com.sep.sep_backend.tripManagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TripInviteServiceTest {

  @Mock private TripRepository tripRepository;
  @Mock private EventRepository eventRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserTripRepository userTripRepository;
  @Mock private UserEventRepository userEventRepository;
  @Mock private TripInviteNotificationService notificationService;

  private TripInviteService newService() {
    return new TripInviteService(
        tripRepository,
        eventRepository,
        userRepository,
        userTripRepository,
        userEventRepository,
        notificationService);
  }

  @Test
  void inviteFriendToTripCreatesPendingEntryAndNotifies() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    UserTrip creatorEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.ACCEPTED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    currentUser.getFriends().add(friend);
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(creatorEntry));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.empty());
    when(eventRepository.findByTrip_Id(10L)).thenReturn(List.of());

    TripInviteResponseDTO result = service.inviteFriendToTrip("max", 10L, 2L);

    assertThat(result.message()).isEqualTo("Trip invite sent");
    verify(userTripRepository).save(any(UserTrip.class));
    verify(notificationService).notifyNewInvite(friend, trip, currentUser);
  }

  @Test
  void inviteFriendToTripCreatesPendingEventInvitesForExistingTripEvents() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    Event firstEvent = event(20L, trip);
    Event secondEvent = event(21L, trip);
    UserTrip creatorEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.ACCEPTED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    currentUser.getFriends().add(friend);
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(creatorEntry));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.empty());
    when(eventRepository.findByTrip_Id(10L)).thenReturn(List.of(firstEvent, secondEvent));
    when(userEventRepository.findByEvent_IdAndUser_Id(20L, 2L)).thenReturn(Optional.empty());
    when(userEventRepository.findByEvent_IdAndUser_Id(21L, 2L)).thenReturn(Optional.empty());

    service.inviteFriendToTrip("max", 10L, 2L);

    ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(UserEvent::getEvent)
        .containsExactly(firstEvent, secondEvent);
    assertThat(captor.getAllValues()).allMatch(userEvent -> userEvent.getUser() == friend);
    assertThat(captor.getAllValues()).allMatch(userEvent -> userEvent.getCreator() == currentUser);
    assertThat(captor.getAllValues())
        .allMatch(userEvent -> userEvent.getStatus() == UserEvent.InviteStatus.PENDING);
  }

  @Test
  void inviteFriendToTripRejectsSelfInvite() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    Trip trip = trip(10L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 1L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void inviteFriendToTripRejectsNonFriend() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User stranger = user(3L, "berta");
    Trip trip = trip(10L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(3L)).thenReturn(Optional.of(stranger));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 3L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void inviteFriendToTripRejectsWhenInviterNotAcceptedMember() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    currentUser.getFriends().add(friend);
    UserTrip pendingEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(pendingEntry));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(userTripRepository, never()).save(any(UserTrip.class));
  }

  @Test
  void inviteFriendToTripRejectsWhenFriendAlreadyAccepted() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    currentUser.getFriends().add(friend);
    UserTrip creatorEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip friendEntry = new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.ACCEPTED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(creatorEntry));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.of(friendEntry));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void inviteFriendToTripRejectsDuplicatePendingInvite() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    currentUser.getFriends().add(friend);
    UserTrip creatorEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip friendEntry = new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(creatorEntry));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.of(friendEntry));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void inviteFriendToTripResetsDeclinedEntryToPending() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    currentUser.getFriends().add(friend);
    UserTrip creatorEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip declinedEntry =
        new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.DECLINED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(creatorEntry));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.of(declinedEntry));
    when(eventRepository.findByTrip_Id(10L)).thenReturn(List.of());

    service.inviteFriendToTrip("max", 10L, 2L);

    assertThat(declinedEntry.getStatus()).isEqualTo(UserTrip.InviteStatus.PENDING);
    verify(userTripRepository).save(declinedEntry);
    verify(notificationService).notifyNewInvite(friend, trip, currentUser);
  }

  @Test
  void inviteFriendToTripResetsDeclinedEventInviteToPending() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    Event event = event(20L, trip);
    UserTrip creatorEntry = new UserTrip(currentUser, trip, null, UserTrip.InviteStatus.ACCEPTED);
    UserTrip declinedTripEntry =
        new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.DECLINED);
    UserEvent declinedEventInvite = new UserEvent(event, friend, currentUser);
    declinedEventInvite.setStatus(UserEvent.InviteStatus.DECLINED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    currentUser.getFriends().add(friend);
    when(userTripRepository.findByUserAndTrip(currentUser, trip))
        .thenReturn(Optional.of(creatorEntry));
    when(userTripRepository.findByUserAndTrip(friend, trip))
        .thenReturn(Optional.of(declinedTripEntry));
    when(eventRepository.findByTrip_Id(10L)).thenReturn(List.of(event));
    when(userEventRepository.findByEvent_IdAndUser_Id(20L, 2L))
        .thenReturn(Optional.of(declinedEventInvite));

    service.inviteFriendToTrip("max", 10L, 2L);

    assertThat(declinedEventInvite.getStatus()).isEqualTo(UserEvent.InviteStatus.PENDING);
    assertThat(declinedEventInvite.getCreator()).isSameAs(currentUser);
    verify(userEventRepository).save(declinedEventInvite);
  }

  @Test
  void respondToTripInviteAcceptsPendingInvite() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    User invitedBy = user(1L, "max");
    Trip trip = trip(10L);
    UserTrip entry = new UserTrip(currentUser, trip, invitedBy, UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));

    TripInviteResponseDTO result = service.respondToTripInvite("anna", 10L, true);

    assertThat(entry.getStatus()).isEqualTo(UserTrip.InviteStatus.ACCEPTED);
    assertThat(result.message()).isEqualTo("Trip invite accepted");
    verify(notificationService).notifyInviteAnswered(entry);
  }

  @Test
  void respondToTripInviteDeclinesPendingInvite() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    User invitedBy = user(1L, "max");
    Trip trip = trip(10L);
    UserTrip entry = new UserTrip(currentUser, trip, invitedBy, UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));

    TripInviteResponseDTO result = service.respondToTripInvite("anna", 10L, false);

    assertThat(entry.getStatus()).isEqualTo(UserTrip.InviteStatus.DECLINED);
    assertThat(result.message()).isEqualTo("Trip invite declined");
  }

  @Test
  void respondToTripInviteRejectsAlreadyAnsweredInvite() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    User invitedBy = user(1L, "max");
    Trip trip = trip(10L);
    UserTrip entry = new UserTrip(currentUser, trip, invitedBy, UserTrip.InviteStatus.ACCEPTED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));

    assertThatThrownBy(() -> service.respondToTripInvite("anna", 10L, true))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void respondToTripInviteThrowsNotFoundWhenNoInviteExists() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    Trip trip = trip(10L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.respondToTripInvite("anna", 10L, true))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getIncomingTripInvitesReturnsOnlyPendingEntriesForUser() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    Trip trip = trip(10L);
    UserTrip pendingEntry =
        new UserTrip(currentUser, trip, user(1L, "max"), UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(userTripRepository.findByUser_IdAndStatus(2L, UserTrip.InviteStatus.PENDING))
        .thenReturn(List.of(pendingEntry));

    List<UserTrip> result = service.getIncomingTripInvites("anna");

    assertThat(result).containsExactly(pendingEntry);
  }

  private User user(long id, String username) {
    User user = new User();
    user.setId(id);
    user.setName(username);
    return user;
  }

  private Trip trip(long id) {
    Trip trip = new Trip("Test-Trip");
    trip.setId(id);
    return trip;
  }

  private Event event(long id, Trip trip) {
    Event event =
        new Event(
            "Event " + id,
            LocalDateTime.of(2026, 6, 1, 12, 0),
            LocalDateTime.of(2026, 6, 1, 13, 0),
            0,
            trip);
    event.setId(id);
    return event;
  }
}
