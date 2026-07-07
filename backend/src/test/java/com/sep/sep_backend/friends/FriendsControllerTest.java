package com.sep.sep_backend.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class FriendsControllerTest {

  @Test
  void controllerHasExpectedMappings() throws NoSuchMethodException {
    assertThat(FriendsController.class.getAnnotation(RestController.class)).isNotNull();

    RequestMapping classMapping = FriendsController.class.getAnnotation(RequestMapping.class);
    assertThat(classMapping.value()).containsExactly("/api/friends");

    Method getFriendsMethod =
        FriendsController.class.getDeclaredMethod("getFriends", Authentication.class);
    assertThat(getFriendsMethod.getAnnotation(GetMapping.class).value()).isEmpty();

    Method searchMethod =
        FriendsController.class.getDeclaredMethod(
            "searchUsers", String.class, Authentication.class);
    assertThat(searchMethod.getAnnotation(GetMapping.class).value()).containsExactly("/search");

    Method removeMethod =
        FriendsController.class.getDeclaredMethod("removeFriend", long.class, Authentication.class);
    assertThat(removeMethod.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{userId}");

    Method sendMethod =
        FriendsController.class.getDeclaredMethod(
            "sendFriendRequest", long.class, Authentication.class);
    assertThat(sendMethod.getAnnotation(PostMapping.class).value())
        .containsExactly("/requests/{userId}");

    Method acceptMethod =
        FriendsController.class.getDeclaredMethod(
            "acceptFriendRequest", long.class, Authentication.class);
    assertThat(acceptMethod.getAnnotation(PostMapping.class).value())
        .containsExactly("/requests/{requestId}/accept");

    Method declineMethod =
        FriendsController.class.getDeclaredMethod(
            "declineFriendRequest", long.class, Authentication.class);
    assertThat(declineMethod.getAnnotation(PostMapping.class).value())
        .containsExactly("/requests/{requestId}/decline");

    Method incomingMethod =
        FriendsController.class.getDeclaredMethod("getIncomingRequests", Authentication.class);
    assertThat(incomingMethod.getAnnotation(GetMapping.class).value())
        .containsExactly("/requests/incoming");

    Method outgoingMethod =
        FriendsController.class.getDeclaredMethod("getOutgoingRequests", Authentication.class);
    assertThat(outgoingMethod.getAnnotation(GetMapping.class).value())
        .containsExactly("/requests/outgoing");
  }

  @Test
  void getFriendsDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    List<FriendDTO> friends = List.of(new FriendDTO(2L, "anna", "Anna", "Muster", true));

    when(friendsService.getFriends("max")).thenReturn(friends);

    List<FriendDTO> result = friendsController.getFriends(authentication);

    assertThat(result).isSameAs(friends);
    verify(friendsService).getFriends("max");
  }

  @Test
  void searchUsersDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    List<FriendDTO> users = List.of(new FriendDTO(2L, "anna", "Anna", "Muster", false));

    when(friendsService.searchUsers("max", "ann")).thenReturn(users);

    List<FriendDTO> result = friendsController.searchUsers("ann", authentication);

    assertThat(result).isSameAs(users);
    verify(friendsService).searchUsers("max", "ann");
  }

  @Test
  void removeFriendDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");

    friendsController.removeFriend(2L, authentication);

    verify(friendsService).removeFriend("max", 2L);
  }

  @Test
  void sendFriendRequestDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    FriendRequestDTO request =
        new FriendRequestDTO(1L, 1L, "max", 2L, "anna", FriendRequestStatus.PENDING);

    when(friendsService.sendFriendRequest("max", 2L)).thenReturn(request);

    FriendRequestDTO result = friendsController.sendFriendRequest(2L, authentication);

    assertThat(result).isSameAs(request);
    verify(friendsService).sendFriendRequest("max", 2L);
  }

  @Test
  void acceptFriendRequestDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    FriendRequestDTO request =
        new FriendRequestDTO(1L, 2L, "anna", 1L, "max", FriendRequestStatus.ACCEPTED);

    when(friendsService.acceptFriendRequest("max", 1L)).thenReturn(request);

    FriendRequestDTO result = friendsController.acceptFriendRequest(1L, authentication);

    assertThat(result).isSameAs(request);
    verify(friendsService).acceptFriendRequest("max", 1L);
  }

  @Test
  void declineFriendRequestDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    FriendRequestDTO request =
        new FriendRequestDTO(1L, 2L, "anna", 1L, "max", FriendRequestStatus.DECLINED);

    when(friendsService.declineFriendRequest("max", 1L)).thenReturn(request);

    FriendRequestDTO result = friendsController.declineFriendRequest(1L, authentication);

    assertThat(result).isSameAs(request);
    verify(friendsService).declineFriendRequest("max", 1L);
  }

  @Test
  void getIncomingRequestsDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    List<FriendRequestDTO> requests =
        List.of(new FriendRequestDTO(1L, 2L, "anna", 1L, "max", FriendRequestStatus.PENDING));

    when(friendsService.getIncomingRequests("max")).thenReturn(requests);

    List<FriendRequestDTO> result = friendsController.getIncomingRequests(authentication);

    assertThat(result).isSameAs(requests);
    verify(friendsService).getIncomingRequests("max");
  }

  @Test
  void getOutgoingRequestsDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    List<FriendRequestDTO> requests =
        List.of(new FriendRequestDTO(1L, 1L, "max", 2L, "anna", FriendRequestStatus.PENDING));

    when(friendsService.getOutgoingRequests("max")).thenReturn(requests);

    List<FriendRequestDTO> result = friendsController.getOutgoingRequests(authentication);

    assertThat(result).isSameAs(requests);
    verify(friendsService).getOutgoingRequests("max");
  }

  @Test
  void rejectsUnauthenticatedRequests() {
    FriendsController friendsController = new FriendsController(mock(FriendsService.class));
    Authentication authentication = mock(Authentication.class);

    when(authentication.isAuthenticated()).thenReturn(false);

    assertThatThrownBy(() -> friendsController.getFriends(authentication))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private Authentication authentication(String username) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn(username);
    return authentication;
  }
}
