package com.sep.sep_backend.tripManagement.services;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
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
  private final UserEventRepository userEventRepository;

  public TripInviteService(
      TripRepository tripRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository) {
    this.tripRepository = tripRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.userEventRepository = userEventRepository;
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

  @Transactional
  public void removeFriendFromTrip(String currentUsername, long tripId, long friendId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    getUserOrThrow(friendId);
    getTripOrThrow(tripId);

    if (currentUser.getId() == friendId) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot remove themselves");
    }

    UserTrip currentMembership =
        userTripRepository
            .findByIdUserIdAndIdTripId(currentUser.getId(), tripId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "User is not part of this trip"));

    UserTrip friendMembership =
        userTripRepository
            .findByIdUserIdAndIdTripId(friendId, tripId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User is not part of this trip"));

    if (!canRemoveTripParticipant(currentMembership, friendMembership, currentUser)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "User cannot remove this trip participant");
    }

    userEventRepository.deleteByEvent_Trip_IdAndUser_Id(tripId, friendId);
    userTripRepository.delete(friendMembership);
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

  private boolean canRemoveTripParticipant(
      UserTrip currentMembership, UserTrip friendMembership, User currentUser) {
    User invitedBy = friendMembership.getInvitedBy();
    boolean currentUserInvitedFriend =
        invitedBy != null && invitedBy.getId().equals(currentUser.getId());
    boolean currentUserCreatedTrip = currentMembership.getInvitedBy() == null;

    return currentUserInvitedFriend || currentUserCreatedTrip;
  }
}
