package com.sep.sep_backend.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.FriendRequest;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.FriendRequestRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FriendsServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private FriendRequestRepository friendRequestRepository;
  @Mock private FriendRequestNotificationService notificationService;

  private FriendsService newService() {
    return new FriendsService(userRepository, friendRequestRepository, notificationService);
  }

  @Test
  void getFriendsReturnsConnectedFriendsOfCurrentUser() {
    FriendsService friendsService = newService();
    User currentUser = user(1L, "max", "Max", "Muster");
    User friend = user(2L, "anna", "Anna", "Beispiel");
    currentUser.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));

    List<FriendDTO> result = friendsService.getFriends("max");

    assertThat(result).containsExactly(new FriendDTO(2L, "anna", "Anna", "Beispiel", true));
  }

  @Test
  void searchUsersExcludesCurrentUserAndMarksExistingFriends() {
    FriendsService friendsService = newService();
    User currentUser = user(1L, "max", "Max", "Muster");
    User friend = user(2L, "anna", "Anna", "Beispiel");
    User stranger = user(3L, "berta", "Berta", "Beispiel");
    currentUser.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository
            .findByNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(
                "be", "be", "be"))
        .thenReturn(List.of(currentUser, friend, stranger));

    List<FriendDTO> result = friendsService.searchUsers("max", "be");

    assertThat(result)
        .containsExactly(
            new FriendDTO(2L, "anna", "Anna", "Beispiel", true),
            new FriendDTO(3L, "berta", "Berta", "Beispiel", false));
  }

  @Test
  void removeFriendRemovesBidirectionalConnection() {
    FriendsService friendsService = newService();
    User currentUser = user(1L, "max", "Max", "Muster");
    User friend = user(2L, "anna", "Anna", "Beispiel");
    currentUser.getFriends().add(friend);
    friend.getFriends().add(currentUser);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));

    friendsService.removeFriend("max", 2L);

    assertThat(currentUser.getFriends()).doesNotContain(friend);
    assertThat(friend.getFriends()).doesNotContain(currentUser);
    verify(userRepository).save(currentUser);
    verify(userRepository).save(friend);
  }

  @Test
  void throwsUnauthorizedWhenCurrentUserDoesNotExist() {
    FriendsService friendsService = newService();

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("missing", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> friendsService.getFriends("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void sendFriendRequestCreatesPendingRequestAndNotifiesReceiver() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(sender));
    when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findBySenderAndReceiverAndStatus(
            sender, receiver, FriendRequestStatus.PENDING))
        .thenReturn(Optional.empty());
    when(friendRequestRepository.findBySenderAndReceiverAndStatus(
            receiver, sender, FriendRequestStatus.PENDING))
        .thenReturn(Optional.empty());

    FriendRequestDTO result = friendsService.sendFriendRequest("max", 2L);

    assertThat(result.status()).isEqualTo(FriendRequestStatus.PENDING);
    assertThat(result.senderUsername()).isEqualTo("max");
    assertThat(result.receiverUsername()).isEqualTo("anna");
    verify(friendRequestRepository).save(any(FriendRequest.class));
    verify(notificationService).notifyNewRequest(any(FriendRequestDTO.class));
  }

  @Test
  void sendFriendRequestRejectsSelfRequest() {
    FriendsService friendsService = newService();
    User currentUser = user(1L, "max", "Max", "Muster");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

    assertThatThrownBy(() -> friendsService.sendFriendRequest("max", 1L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void sendFriendRequestRejectsWhenAlreadyFriends() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    sender.getFriends().add(receiver);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(sender));
    when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));

    assertThatThrownBy(() -> friendsService.sendFriendRequest("max", 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void sendFriendRequestRejectsDuplicatePendingRequest() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    FriendRequest existing = new FriendRequest(sender, receiver);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(sender));
    when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findBySenderAndReceiverAndStatus(
            sender, receiver, FriendRequestStatus.PENDING))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> friendsService.sendFriendRequest("max", 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void sendFriendRequestAutoAcceptsExistingReverseRequest() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    FriendRequest reverseRequest = new FriendRequest(receiver, sender);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(sender));
    when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findBySenderAndReceiverAndStatus(
            sender, receiver, FriendRequestStatus.PENDING))
        .thenReturn(Optional.empty());
    when(friendRequestRepository.findBySenderAndReceiverAndStatus(
            receiver, sender, FriendRequestStatus.PENDING))
        .thenReturn(Optional.of(reverseRequest));

    FriendRequestDTO result = friendsService.sendFriendRequest("max", 2L);

    assertThat(result.status()).isEqualTo(FriendRequestStatus.ACCEPTED);
    assertThat(sender.getFriends()).contains(receiver);
    assertThat(receiver.getFriends()).contains(sender);
    verify(friendRequestRepository, times(1)).save(reverseRequest);
    verify(friendRequestRepository, times(1))
        .save(any(FriendRequest.class)); // genau ein save insgesamt
    verify(notificationService).notifyRequestAnswered(any(FriendRequestDTO.class));
  }

  @Test
  void acceptFriendRequestCreatesBidirectionalConnection() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    FriendRequest request = new FriendRequest(sender, receiver);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findByIdAndReceiver(1L, receiver))
        .thenReturn(Optional.of(request));

    FriendRequestDTO result = friendsService.acceptFriendRequest("anna", 1L);

    assertThat(result.status()).isEqualTo(FriendRequestStatus.ACCEPTED);
    assertThat(sender.getFriends()).contains(receiver);
    assertThat(receiver.getFriends()).contains(sender);
    verify(notificationService).notifyRequestAnswered(any(FriendRequestDTO.class));
  }

  @Test
  void acceptFriendRequestRejectsAlreadyAnsweredRequest() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    FriendRequest request = new FriendRequest(sender, receiver);
    request.setStatus(FriendRequestStatus.DECLINED);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findByIdAndReceiver(1L, receiver))
        .thenReturn(Optional.of(request));

    assertThatThrownBy(() -> friendsService.acceptFriendRequest("anna", 1L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void declineFriendRequestSetsStatusWithoutCreatingConnection() {
    FriendsService friendsService = newService();
    User sender = user(1L, "max", "Max", "Muster");
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    FriendRequest request = new FriendRequest(sender, receiver);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findByIdAndReceiver(1L, receiver))
        .thenReturn(Optional.of(request));

    FriendRequestDTO result = friendsService.declineFriendRequest("anna", 1L);

    assertThat(result.status()).isEqualTo(FriendRequestStatus.DECLINED);
    assertThat(sender.getFriends()).doesNotContain(receiver);
    assertThat(receiver.getFriends()).doesNotContain(sender);
    verify(notificationService).notifyRequestAnswered(any(FriendRequestDTO.class));
  }

  @Test
  void getIncomingRequestsReturnsPendingRequestsForReceiver() {
    FriendsService friendsService = newService();
    User receiver = user(2L, "anna", "Anna", "Beispiel");
    User sender = user(1L, "max", "Max", "Muster");
    FriendRequest request = new FriendRequest(sender, receiver);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("anna", "anna"))
        .thenReturn(Optional.of(receiver));
    when(friendRequestRepository.findByReceiverAndStatus(receiver, FriendRequestStatus.PENDING))
        .thenReturn(List.of(request));

    List<FriendRequestDTO> result = friendsService.getIncomingRequests("anna");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).senderUsername()).isEqualTo("max");
  }

  private User user(long id, String username, String firstName, String surname) {
    User user = new User();
    user.setId(id);
    user.setName(username);
    user.setFirstName(firstName);
    user.setSurname(surname);
    return user;
  }
}
