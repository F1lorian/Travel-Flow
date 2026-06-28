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

    Method addMethod =
        FriendsController.class.getDeclaredMethod("addFriend", long.class, Authentication.class);
    assertThat(addMethod.getAnnotation(PostMapping.class).value()).containsExactly("/{userId}");

    Method removeMethod =
        FriendsController.class.getDeclaredMethod("removeFriend", long.class, Authentication.class);
    assertThat(removeMethod.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{userId}");
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
  void addFriendDelegatesToServiceWithAuthenticatedUsername() {
    FriendsService friendsService = mock(FriendsService.class);
    FriendsController friendsController = new FriendsController(friendsService);
    Authentication authentication = authentication("max");
    FriendDTO friend = new FriendDTO(2L, "anna", "Anna", "Muster", true);

    when(friendsService.addFriend("max", 2L)).thenReturn(friend);

    FriendDTO result = friendsController.addFriend(2L, authentication);

    assertThat(result).isSameAs(friend);
    verify(friendsService).addFriend("max", 2L);
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
