package com.sep.sep_backend.tripManagement.services;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TripInviteService {
  private final TripRepository tripRepository;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;

  public TripInviteService(
      TripRepository tripRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository) {
    this.tripRepository = tripRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
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
