package com.sep.sep_backend.tripManagement.services;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service that handles the Invitations to trips as well as the responses. Sits between {@link
 * com.sep.sep_backend.tripManagement.controllers.TripInviteController} and * the JPA repositories
 */
@Service
public class TripInviteService {
  private final TripRepository tripRepository;
  private final EventRepository eventRepository;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;
  private final UserEventRepository userEventRepository;
  private final TripInviteNotificationService notificationService;

  public TripInviteService(
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository,
      TripInviteNotificationService notificationService) {
    this.tripRepository = tripRepository;
    this.eventRepository = eventRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.userEventRepository = userEventRepository;
    this.notificationService = notificationService;
  }

  /**
   * sends an invitation to the friend (friendId)
   *
   * @param currentUsername
   * @param tripId
   * @param friendId
   * @return responseDTO with the tripId, friendId and a "trip invite sent" message
   */
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

    if (!hasAcceptedTrip(currentUser, trip)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not part of this trip");
    }

    var existingEntry = userTripRepository.findByUserAndTrip(friend, trip);

    if (existingEntry.isPresent()) {
      UserTrip entry = existingEntry.get();
      if (entry.getStatus() == UserTrip.InviteStatus.ACCEPTED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already part of this trip");
      }
      if (entry.getStatus() == UserTrip.InviteStatus.PENDING) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite already sent");
      }
      // DECLINED -> erneute Einladung erlaubt: bestehende Zeile zurücksetzen,
      // da die zusammengesetzte ID (user, trip) keinen zweiten Insert erlaubt.
      entry.setStatus(UserTrip.InviteStatus.PENDING);
      entry.setInvitedBy(currentUser);
      userTripRepository.save(entry);
      notificationService.notifyNewInvite(friend, trip, currentUser);
    } else {
      UserTrip invite = new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.PENDING);
      userTripRepository.save(invite);
      notificationService.notifyNewInvite(friend, trip, currentUser);
    }

    inviteFriendToExistingTripEvents(trip, friend, currentUser);

    return new TripInviteResponseDTO(trip.getId(), friend.getId(), "Trip invite sent");
  }

  private void inviteFriendToExistingTripEvents(Trip trip, User friend, User invitedBy) {
    for (Event event : eventRepository.findByTrip_Id(trip.getId())) {
      var existingInvite =
          userEventRepository.findByEvent_IdAndUser_Id(event.getId(), friend.getId());
      if (existingInvite.isPresent()) {
        UserEvent userEvent = existingInvite.get();
        if (userEvent.getStatus() == UserEvent.InviteStatus.DECLINED) {
          userEvent.setStatus(UserEvent.InviteStatus.PENDING);
          userEvent.setCreator(invitedBy);
          userEventRepository.save(userEvent);
        }
        continue;
      }

      userEventRepository.save(new UserEvent(event, friend, invitedBy));
    }
  }

  /**
   * lets the user respond to the invite and either accept or decline it
   *
   * @param currentUsername
   * @param tripId
   * @param accept either true => ACCEPTED or false => DECLINED
   * @return returns a TripInviteResponseDTO with the tripId, the userId and a message with either
   *     accepted or declined
   */
  @Transactional
  public TripInviteResponseDTO respondToTripInvite(
      String currentUsername, long tripId, boolean accept) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    Trip trip = getTripOrThrow(tripId);

    UserTrip entry =
        userTripRepository
            .findByUserAndTrip(currentUser, trip)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));

    if (entry.getStatus() != UserTrip.InviteStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite has already been answered");
    }

    entry.setStatus(accept ? UserTrip.InviteStatus.ACCEPTED : UserTrip.InviteStatus.DECLINED);
    userTripRepository.save(entry);

    notificationService.notifyInviteAnswered(entry);

    return new TripInviteResponseDTO(
        trip.getId(),
        currentUser.getId(),
        accept ? "Trip invite accepted" : "Trip invite declined");
  }

  /**
   * returns a list of all incoming and unanswered tripInvite a user currently has
   *
   * @param currentUsername
   * @return
   */
  public List<UserTrip> getIncomingTripInvites(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    return userTripRepository.findByUser_IdAndStatus(
        currentUser.getId(), UserTrip.InviteStatus.PENDING);
  }

  /**
   * searches the user from the database using the username
   *
   * @param username
   * @return
   */
  private User getCurrentUserOrThrow(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
  }

  /**
   * searches the user from the database using the id
   *
   * @param userId
   * @return
   */
  private User getUserOrThrow(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  /**
   * searches the trip from the database with the id
   *
   * @param tripId
   * @return
   */
  private Trip getTripOrThrow(long tripId) {
    return tripRepository
        .findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
  }

  /**
   * checks if the 2 users are friends
   *
   * @param currentUser
   * @param friend
   * @return
   */
  private boolean isFriend(User currentUser, User friend) {
    return currentUser.getFriends().stream()
        .anyMatch(existingFriend -> existingFriend.getId() == friend.getId());
  }

  /**
   * checks if the user has already accepted his invite to the trip
   *
   * @param user
   * @param trip
   * @return
   */
  private boolean hasAcceptedTrip(User user, Trip trip) {
    return userTripRepository
        .findByUserAndTrip(user, trip)
        .filter(entry -> entry.getStatus() == UserTrip.InviteStatus.ACCEPTED)
        .isPresent();
  }
}
