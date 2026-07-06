package com.sep.sep_backend.privacy;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.PrivacyPermission;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.PrivacyPermissionRepository;
import com.sep.sep_backend.repos.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service containing the privacy permission logic.
 *
 * <p>Privacy settings are configured per friend. A friendship alone does not grant access to
 * protected information. The owner must explicitly allow access to events or profile information.
 */
@Service
public class PrivacyService {
  private final UserRepository userRepository;
  private final PrivacyPermissionRepository privacyPermissionRepository;

  public PrivacyService(
      UserRepository userRepository, PrivacyPermissionRepository privacyPermissionRepository) {
    this.userRepository = userRepository;
    this.privacyPermissionRepository = privacyPermissionRepository;
  }

  /**
   * Returns the privacy permissions that the current user configured for all friends.
   *
   * <p>If a friend does not have a permission entry yet, the default values are false.
   */
  public List<PrivacyPermissionDTO> getPermissions(String currentUsername) {
    User owner = getCurrentUserOrThrow(currentUsername);

    return owner.getFriends().stream().map(friend -> toPermissionDTO(owner, friend)).toList();
  }

  /**
   * Updates the privacy permission for one friend.
   *
   * <p>The target user must be a friend of the current user. Otherwise, no permissions can be set.
   */
  @Transactional
  public PrivacyPermissionDTO updatePermission(
      String currentUsername, long friendId, UpdatePrivacyPermissionRequestDTO request) {
    User owner = getCurrentUserOrThrow(currentUsername);
    User friend = getUserOrThrow(friendId);

    if (!isFriend(owner, friend)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Permissions can only be changed for friends");
    }

    PrivacyPermission permission =
        privacyPermissionRepository
            .findByOwnerAndFriend(owner, friend)
            .orElseGet(() -> new PrivacyPermission(owner, friend));

    permission.setCanSeeEvents(request.canSeeEvents());
    permission.setCanSeeProfile(request.canSeeProfile());

    privacyPermissionRepository.save(permission);

    return toPermissionDTO(owner, friend);
  }

  /** Returns profile information of another user if the current user is allowed to see it. */
  public SharedUserProfileDTO getSharedProfile(String currentUsername, long ownerId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User owner = getUserOrThrow(ownerId);

    PrivacyPermission permission = getPermissionOrForbidden(owner, currentUser);

    if (!permission.isCanSeeProfile()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You are not allowed to see this profile");
    }

    return new SharedUserProfileDTO(
        owner.getId(), owner.getName(), owner.getFirstName(), owner.getSurname());
  }

  /**
   * Returns events of another user if the current user is allowed to see them.
   *
   * <p>Events are collected from the owner's directly assigned events.
   */
  public List<SharedEventDTO> getSharedEvents(String currentUsername, long ownerId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User owner = getUserOrThrow(ownerId);

    PrivacyPermission permission = getPermissionOrForbidden(owner, currentUser);

    if (!permission.isCanSeeEvents()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You are not allowed to see these events");
    }

    if (owner.getEvents() == null) {
      return List.of();
    }

    return owner.getEvents().stream().map(this::toSharedEventDTO).toList();
  }

  private PrivacyPermissionDTO toPermissionDTO(User owner, User friend) {
    PrivacyPermission permission =
        privacyPermissionRepository
            .findByOwnerAndFriend(owner, friend)
            .orElse(new PrivacyPermission(owner, friend));

    return new PrivacyPermissionDTO(
        friend.getId(),
        friend.getName(),
        friend.getFirstName(),
        friend.getSurname(),
        permission.isCanSeeEvents(),
        permission.isCanSeeProfile());
  }

  private SharedEventDTO toSharedEventDTO(Event event) {
    return new SharedEventDTO(
        event.getId(),
        event.getName(),
        event.getStartTime(),
        event.getEndTime(),
        event.getKosten(),
        event.getTripId());
  }

  private PrivacyPermission getPermissionOrForbidden(User owner, User friend) {
    if (!isFriend(owner, friend)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only friends can access this information");
    }

    return privacyPermissionRepository
        .findByOwnerAndFriend(owner, friend)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No privacy permission was granted"));
  }

  private boolean isFriend(User user, User possibleFriend) {
    return user.getFriends().stream().anyMatch(friend -> friend.getId() == possibleFriend.getId());
  }

  private User getCurrentUserOrThrow(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
  }

  private User getUserOrThrow(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}
