package com.sep.sep_backend.tripManagement.services;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds a friend to a trip ("invite"). Only members can invite, only friends can be invited, and the
 * new member is notified by email so they learn about the trip even while logged out.
 */
@Service
public class TripInviteService {
  private final TripRepository tripRepository;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;
  private final EmailNotificationService emailNotificationService;

  public TripInviteService(
      TripRepository tripRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      EmailNotificationService emailNotificationService) {
    this.tripRepository = tripRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.emailNotificationService = emailNotificationService;
  }

  @Transactional
  public TripInviteResponseDTO inviteFriendToTrip(
      String currentUsername, long tripId, long friendId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User friend = getUserOrThrow(friendId);
    Trip trip = getTripOrThrow(tripId);

    if (currentUser.getId() == friend.getId()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot invite themselves");
    }

    if (!isFriend(currentUser, friend)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a friend");
    }

    if (!hasTrip(currentUser, trip)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not part of this trip");
    }

    if (!hasTrip(friend, trip)) {
      userTripRepository.save(new UserTrip(friend, trip, currentUser));
      emailNotificationService.sendBestEffort(
          friend.getEmail(),
          "You were added to the trip \"" + trip.getName() + "\"",
          currentUser.getName()
              + " added you to the trip \""
              + trip.getName()
              + "\". Log in to SEP to see its events and expenses.");
      // The inviter's in-app notification is mirrored to their inbox as well.
      emailNotificationService.sendBestEffort(
          currentUser.getEmail(),
          "Invite sent for trip \"" + trip.getName() + "\"",
          "You invited " + friend.getName() + " to the trip \"" + trip.getName() + "\".");
    }

    return new TripInviteResponseDTO(trip.getId(), friend.getId(), "Trip invite sent");
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

  private Trip getTripOrThrow(long tripId) {
    return tripRepository
        .findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
  }

  private boolean isFriend(User currentUser, User friend) {
    return currentUser.getFriends().stream()
        .anyMatch(existingFriend -> existingFriend.getId() == friend.getId());
  }

  private boolean hasTrip(User user, Trip trip) {
    return userTripRepository.existsByIdUserIdAndIdTripId(user.getId(), trip.getId());
  }
}
