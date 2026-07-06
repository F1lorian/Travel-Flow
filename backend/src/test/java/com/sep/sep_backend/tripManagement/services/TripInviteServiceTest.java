package com.sep.sep_backend.tripManagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TripInviteServiceTest {

  private final TripRepository tripRepository = org.mockito.Mockito.mock(TripRepository.class);
  private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
  private final UserTripRepository userTripRepository =
      org.mockito.Mockito.mock(UserTripRepository.class);
  private final EmailNotificationService emailNotificationService =
      org.mockito.Mockito.mock(EmailNotificationService.class);
  private final TripInviteService tripInviteService =
      new TripInviteService(
          tripRepository, userRepository, userTripRepository, emailNotificationService);

  @Test
  void inviteFriendToTripAddsTripToFriend() {
    Trip trip = trip(3L);
    User currentUser = user(1L);
    User friend = user(2L);
    currentUser.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(tripRepository.findById(3L)).thenReturn(Optional.of(trip));
    when(userTripRepository.existsByIdUserIdAndIdTripId(1L, 3L)).thenReturn(true);
    when(userTripRepository.existsByIdUserIdAndIdTripId(2L, 3L)).thenReturn(false);

    TripInviteResponseDTO response = tripInviteService.inviteFriendToTrip("max", 3L, 2L);

    assertThat(response.tripId()).isEqualTo(3L);
    assertThat(response.friendId()).isEqualTo(2L);
    verify(userTripRepository).save(any(UserTrip.class));
    // The new member is notified by email so they learn about the trip even while logged out.
    verify(emailNotificationService).sendBestEffort(any(), contains("Berlin"), any());
  }

  @Test
  void inviteFriendToTripRejectsUsersWhoAreNotFriends() {
    Trip trip = trip(3L);
    User currentUser = user(1L);
    User otherUser = user(2L);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
    when(tripRepository.findById(3L)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> tripInviteService.inviteFriendToTrip("max", 3L, 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private Trip trip(long id) {
    Trip trip = new Trip("Berlin");
    trip.setId(id);
    return trip;
  }
}
