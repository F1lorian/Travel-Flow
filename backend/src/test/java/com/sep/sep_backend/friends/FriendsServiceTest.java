package com.sep.sep_backend.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.User;
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

  @Test
  void getFriendsReturnsConnectedFriendsOfCurrentUser() {
    FriendsService friendsService = new FriendsService(userRepository);
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
    FriendsService friendsService = new FriendsService(userRepository);
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
  void addFriendCreatesBidirectionalConnection() {
    FriendsService friendsService = new FriendsService(userRepository);
    User currentUser = user(1L, "max", "Max", "Muster");
    User friend = user(2L, "anna", "Anna", "Beispiel");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));

    FriendDTO result = friendsService.addFriend("max", 2L);

    assertThat(result).isEqualTo(new FriendDTO(2L, "anna", "Anna", "Beispiel", true));
    assertThat(currentUser.getFriends()).contains(friend);
    assertThat(friend.getFriends()).contains(currentUser);
    verify(userRepository).save(currentUser);
    verify(userRepository).save(friend);
  }

  @Test
  void addFriendDoesNotSaveWhenAlreadyConnected() {
    FriendsService friendsService = new FriendsService(userRepository);
    User currentUser = user(1L, "max", "Max", "Muster");
    User friend = user(2L, "anna", "Anna", "Beispiel");
    currentUser.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));

    FriendDTO result = friendsService.addFriend("max", 2L);

    assertThat(result).isEqualTo(new FriendDTO(2L, "anna", "Anna", "Beispiel", true));
    verify(userRepository, never()).save(currentUser);
    verify(userRepository, never()).save(friend);
  }

  @Test
  void addFriendRejectsSelfConnection() {
    FriendsService friendsService = new FriendsService(userRepository);
    User currentUser = user(1L, "max", "Max", "Muster");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

    assertThatThrownBy(() -> friendsService.addFriend("max", 1L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void removeFriendRemovesBidirectionalConnection() {
    FriendsService friendsService = new FriendsService(userRepository);
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
    FriendsService friendsService = new FriendsService(userRepository);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("missing", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> friendsService.getFriends("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void throwsNotFoundWhenFriendDoesNotExist() {
    FriendsService friendsService = new FriendsService(userRepository);
    User currentUser = user(1L, "max", "Max", "Muster");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("max", "max"))
        .thenReturn(Optional.of(currentUser));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> friendsService.addFriend("max", 2L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
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
