package com.sep.sep_backend.tripManagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
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
  @Mock private UserRepository userRepository;
  @Mock private UserTripRepository userTripRepository;
  @Mock private EventRepository eventRepository;
  @Mock private UserEventRepository userEventRepository;
  @Mock private TripInviteNotificationService notificationService;
  @Mock private EmailNotificationService emailNotificationService;

  private TripInviteService newService() {
    return new TripInviteService(
        tripRepository,
        userRepository,
        userTripRepository,
        eventRepository,
        userEventRepository,
        notificationService,
        emailNotificationService);
  }

  @Test
  void inviteFriendToTripCreatesPendingEntryAndNotifies() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    currentUser.setEmail("max@example.com");
    User friend = user(2L, "anna");
    friend.setEmail("anna@example.com");
    Trip trip = trip(10L);
    trip.setOwner(currentUser);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    currentUser.getFriends().add(friend);
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.empty());

    TripInviteResponseDTO result = service.inviteFriendToTrip("max", 10L, 2L, false);

    assertThat(result.message()).isEqualTo("Trip invite sent");
    verify(userTripRepository).save(any(UserTrip.class));
    verify(notificationService).notifyNewInvite(friend, trip, currentUser);
    // The invited friend is emailed so they learn about the trip even while logged out, and the
    // inviter's in-app notification is mirrored to their inbox as well.
    verify(emailNotificationService).sendBestEffort(eq("anna@example.com"), any(), any());
    verify(emailNotificationService).sendBestEffort(eq("max@example.com"), any(), any());
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

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 1L, false))
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

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 3L, false))
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
    trip.setOwner(user(3L, "owner"));
    currentUser.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 2L, false))
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
    trip.setOwner(currentUser);
    currentUser.getFriends().add(friend);
    UserTrip friendEntry = new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.ACCEPTED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.of(friendEntry));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 2L, false))
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
    trip.setOwner(currentUser);
    currentUser.getFriends().add(friend);
    UserTrip friendEntry = new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.of(friendEntry));

    assertThatThrownBy(() -> service.inviteFriendToTrip("max", 10L, 2L, false))
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
    trip.setOwner(currentUser);
    currentUser.getFriends().add(friend);
    UserTrip declinedEntry =
        new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.DECLINED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.of(declinedEntry));

    service.inviteFriendToTrip("max", 10L, 2L, false);

    assertThat(declinedEntry.getStatus()).isEqualTo(UserTrip.InviteStatus.PENDING);
    verify(userTripRepository).save(declinedEntry);
    verify(notificationService).notifyNewInvite(friend, trip, currentUser);
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

  @Test
  void inviteFriendToTripStoresIncludeEventsFlag() {
    TripInviteService service = newService();
    User currentUser = user(1L, "max");
    User friend = user(2L, "anna");
    Trip trip = trip(10L);
    trip.setOwner(currentUser);
    currentUser.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(friend, trip)).thenReturn(Optional.empty());

    service.inviteFriendToTrip("max", 10L, 2L, true);

    ArgumentCaptor<UserTrip> captor = ArgumentCaptor.forClass(UserTrip.class);
    verify(userTripRepository).save(captor.capture());
    assertThat(captor.getValue().isIncludeEvents()).isTrue();
  }

  @Test
  void acceptingInviteWithIncludeEventsAddsUserToAllTripEvents() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    User invitedBy = user(1L, "max");
    Trip trip = trip(10L);
    UserTrip entry = new UserTrip(currentUser, trip, invitedBy, UserTrip.InviteStatus.PENDING);
    entry.setIncludeEvents(true);

    Event dinner = new Event("Dinner", null, null, 0, trip);
    dinner.setId(100L);
    Event museum = new Event("Museum", null, null, 0, trip);
    museum.setId(101L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));
    when(eventRepository.findByTrip_Id(10L)).thenReturn(List.of(dinner, museum));
    when(userEventRepository.findByEvent_IdAndUser_Id(100L, 2L)).thenReturn(Optional.empty());
    when(userEventRepository.findByEvent_IdAndUser_Id(101L, 2L)).thenReturn(Optional.empty());

    service.respondToTripInvite("anna", 10L, true);

    ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(
            participation -> {
              assertThat(participation.getUser()).isSameAs(currentUser);
              assertThat(participation.getCreator()).isSameAs(invitedBy);
              assertThat(participation.getStatus()).isEqualTo(UserEvent.InviteStatus.ACCEPTED);
            });
    assertThat(captor.getAllValues())
        .extracting(participation -> participation.getEvent().getName())
        .containsExactlyInAnyOrder("Dinner", "Museum");
  }

  @Test
  void acceptingInviteWithIncludeEventsUpgradesExistingParticipation() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    User invitedBy = user(1L, "max");
    Trip trip = trip(10L);
    UserTrip entry = new UserTrip(currentUser, trip, invitedBy, UserTrip.InviteStatus.PENDING);
    entry.setIncludeEvents(true);

    Event dinner = new Event("Dinner", null, null, 0, trip);
    dinner.setId(100L);
    UserEvent pendingInvite = new UserEvent(dinner, currentUser, invitedBy);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));
    when(eventRepository.findByTrip_Id(10L)).thenReturn(List.of(dinner));
    when(userEventRepository.findByEvent_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.of(pendingInvite));

    service.respondToTripInvite("anna", 10L, true);

    assertThat(pendingInvite.getStatus()).isEqualTo(UserEvent.InviteStatus.ACCEPTED);
    verify(userEventRepository).save(pendingInvite);
  }

  @Test
  void acceptingInviteWithoutIncludeEventsLeavesEventsUntouched() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    Trip trip = trip(10L);
    UserTrip entry =
        new UserTrip(currentUser, trip, user(1L, "max"), UserTrip.InviteStatus.PENDING);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));

    service.respondToTripInvite("anna", 10L, true);

    verify(eventRepository, never()).findByTrip_Id(any());
    verify(userEventRepository, never()).save(any(UserEvent.class));
  }

  @Test
  void decliningInviteWithIncludeEventsDoesNotAddEvents() {
    TripInviteService service = newService();
    User currentUser = user(2L, "anna");
    Trip trip = trip(10L);
    UserTrip entry =
        new UserTrip(currentUser, trip, user(1L, "max"), UserTrip.InviteStatus.PENDING);
    entry.setIncludeEvents(true);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(currentUser));
    when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
    when(userTripRepository.findByUserAndTrip(currentUser, trip)).thenReturn(Optional.of(entry));

    service.respondToTripInvite("anna", 10L, false);

    verify(eventRepository, never()).findByTrip_Id(any());
    verify(userEventRepository, never()).save(any(UserEvent.class));
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
}
