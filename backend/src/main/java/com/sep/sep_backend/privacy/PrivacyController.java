package com.sep.sep_backend.privacy;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for privacy settings and protected user information.
 *
 * <p>All endpoints require authentication. The current user is resolved from the JWT
 * authentication.
 */
@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201"})
@RequestMapping("/api/privacy")
public class PrivacyController {
  private final PrivacyService privacyService;

  public PrivacyController(PrivacyService privacyService) {
    this.privacyService = privacyService;
  }

  /** Returns the current user's privacy settings for all friends. */
  @GetMapping("/permissions")
  public List<PrivacyPermissionDTO> getPermissions(Authentication authentication) {
    return privacyService.getPermissions(getCurrentUsername(authentication));
  }

  /** Updates what a specific friend is allowed to see. */
  @PutMapping("/permissions/{friendId}")
  public PrivacyPermissionDTO updatePermission(
      @PathVariable long friendId,
      @RequestBody UpdatePrivacyPermissionRequestDTO request,
      Authentication authentication) {
    return privacyService.updatePermission(getCurrentUsername(authentication), friendId, request);
  }

  /** Returns shared profile information of another user if permission was granted. */
  @GetMapping("/users/{userId}/profile")
  public SharedUserProfileDTO getSharedProfile(
      @PathVariable long userId, Authentication authentication) {
    return privacyService.getSharedProfile(getCurrentUsername(authentication), userId);
  }

  /** Returns shared events of another user if permission was granted. */
  @GetMapping("/users/{userId}/events")
  public List<SharedEventDTO> getSharedEvents(
      @PathVariable long userId, Authentication authentication) {
    return privacyService.getSharedEvents(getCurrentUsername(authentication), userId);
  }

  private String getCurrentUsername(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    return authentication.getName();
  }
}
