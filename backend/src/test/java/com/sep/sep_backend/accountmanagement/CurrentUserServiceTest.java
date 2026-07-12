package com.sep.sep_backend.accountmanagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private CurrentUserService currentUserService;

  @Test
  void requireUser_returnsUser_whenAuthenticated() {
    User user = new User();
    user.setId(7L);
    Authentication authentication = authentication("testuser");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("testuser", "testuser"))
        .thenReturn(Optional.of(user));

    assertEquals(7L, currentUserService.requireUser(authentication).getId());
  }

  @Test
  void requireUser_throws401_whenAuthenticationMissing() {
    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> currentUserService.requireUser(null));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  @Test
  void requireUser_throws401_whenAccountNoLongerExists() {
    Authentication authentication = authentication("ghost");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("ghost", "ghost"))
        .thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> currentUserService.requireUser(authentication));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  private Authentication authentication(String username) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn(username);
    return authentication;
  }
}
