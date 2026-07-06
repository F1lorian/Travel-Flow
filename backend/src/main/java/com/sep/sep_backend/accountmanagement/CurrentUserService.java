package com.sep.sep_backend.accountmanagement;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the {@link User} entity behind the authenticated request. The JWT filter stores only the
 * username in the {@link Authentication}; this service turns it back into the database row so
 * controllers can enforce per-user rules (e.g. "only trip members may delete a trip").
 */
@Service
public class CurrentUserService {

  private final UserRepository userRepository;

  public CurrentUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Returns the logged-in user, or fails with 401 when the request carries no authentication or the
   * account no longer exists (e.g. it was deleted while the token was still valid).
   */
  public User requireUser(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    String username = authentication.getName();
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
  }
}
