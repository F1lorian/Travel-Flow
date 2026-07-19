package com.sep.sep_backend.tripManagement.services;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.notification.EmailNotificationService;
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
 * Service that handles invitations to trips as well as the responses (accept/decline). Only
 * accepted members can invite, only friends can be invited, and every step is mirrored by email so
 * the other side learns about it even while logged out. Sits between {@link
 * com.sep.sep_backend.tripManagement.controllers.TripInviteController} and the JPA repositories.
 */
@Service
public class TripInviteService {
  private final TripRepository tripRepository;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;
  private final EventRepository eventRepository;
  private final UserEventRepository userEventRepository;
  private final TripInviteNotificationService notificationService;
  private final EmailNotificationService emailNotificationService;

  public TripInviteService(
      TripRepository tripRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      EventRepository eventRepository,
      UserEventRepository userEventRepository,
      TripInviteNotificationService notificationService,
      EmailNotificationService emailNotificationService) {
    this.tripRepository = tripRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.eventRepository = eventRepository;
    this.userEventRepository = userEventRepository;
    this.notificationService = notificationService;
    this.emailNotificationService = emailNotificationService;
  }

  /**
   * Sends a pending trip invitation from the authenticated trip owner to one friend.
   *
   * <p>The method rejects self-invites, non-friends, non-owner callers, existing accepted
   * memberships, and duplicate pending invites. Declined invites are reopened as pending.
   *
   * @param currentUsername username or email of the authenticated inviter
   * @param tripId id of the trip to invite into
   * @param friendId id of the friend to invite
   * @param includeEvents when true, accepting the trip invite also adds the friend to all events of
   *     the trip
   * @return response containing the trip id, friend id, and result message
   */
  @Transactional
  public TripInviteResponseDTO inviteFriendToTrip(
      String currentUsername, long tripId, long friendId, boolean includeEvents) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User friend = getUserOrThrow(friendId);
    Trip trip = getTripOrThrow(tripId);

    if (currentUser.getId() == friend.getId()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot invite themselves");
    }

    if (!isFriend(currentUser, friend)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a friend");
    }

    requireOwner(trip, currentUser);

    var existingEntry = userTripRepository.findByUserAndTrip(friend, trip);

    if (existingEntry.isPresent()) {
      UserTrip entry = existingEntry.get();
      if (entry.getStatus() == UserTrip.InviteStatus.ACCEPTED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already part of this trip");
      }
      if (entry.getStatus() == UserTrip.InviteStatus.PENDING) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite already sent");
      }
      entry.setStatus(UserTrip.InviteStatus.PENDING);
      entry.setInvitedBy(currentUser);
      entry.setIncludeEvents(includeEvents);
      userTripRepository.save(entry);
      notificationService.notifyNewInvite(friend, trip, currentUser);
    } else {
      UserTrip invite = new UserTrip(friend, trip, currentUser, UserTrip.InviteStatus.PENDING);
      invite.setIncludeEvents(includeEvents);
      userTripRepository.save(invite);
      notificationService.notifyNewInvite(friend, trip, currentUser);
    }

    // The invited friend learns about the pending invite even while logged out.
    emailNotificationService.sendBestEffort(
        friend.getEmail(),
        "You were invited to the trip \"" + trip.getName() + "\"",
        currentUser.getName()
            + " invited you to the trip \""
            + trip.getName()
            + "\". Log in to SEP to accept or decline.");
    // The inviter's in-app notification is mirrored to their inbox as well.
    emailNotificationService.sendBestEffort(
        currentUser.getEmail(),
        "Invite sent for trip \"" + trip.getName() + "\"",
        "You invited " + friend.getName() + " to the trip \"" + trip.getName() + "\".");

    return new TripInviteResponseDTO(trip.getId(), friend.getId(), "Trip invite sent");
  }

  /**
   * Accepts or declines the authenticated user's pending trip invitation.
   *
   * <p>When the invite is accepted and {@code includeEvents} was set by the inviter, the user is
   * also added as an accepted participant to every event of the trip.
   *
   * @param currentUsername username or email of the invited user
   * @param tripId id of the invited trip
   * @param accept {@code true} to accept the invite, {@code false} to decline it
   * @return response containing the trip id, user id, and result message
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

    // The inviter opted to add the friend to all trip events, so accepting the trip invite is
    // enough — no per-event invites need to be answered.
    if (accept && entry.isIncludeEvents()) {
      addUserToAllTripEvents(entry);
    }

    notificationService.notifyInviteAnswered(entry);

    // Tell the inviter how their invite was answered (skip invites with no recorded inviter).
    User inviter = entry.getInvitedBy();
    if (inviter != null) {
      String answer = accept ? "accepted" : "declined";
      emailNotificationService.sendBestEffort(
          inviter.getEmail(),
          "Your invite to \"" + trip.getName() + "\" was " + answer,
          currentUser.getName()
              + " "
              + answer
              + " your invitation to the trip \""
              + trip.getName()
              + "\".");
    }

    return new TripInviteResponseDTO(
        trip.getId(),
        currentUser.getId(),
        accept ? "Trip invite accepted" : "Trip invite declined");
  }

  /**
   * Returns all pending trip invitations for the authenticated user.
   *
   * @param currentUsername username or email of the invited user
   * @return pending trip membership rows
   */
  public List<UserTrip> getIncomingTripInvites(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    return userTripRepository.findByUser_IdAndStatus(
        currentUser.getId(), UserTrip.InviteStatus.PENDING);
  }

  /**
   * Adds the invitee as an accepted participant to every event of the trip. Existing participation
   * rows (e.g. a pending per-event invite sent earlier) are upgraded to accepted instead of
   * duplicated. The trip inviter is recorded as the creator of each participation.
   *
   * @param entry the accepted trip membership of the invitee
   */
  private void addUserToAllTripEvents(UserTrip entry) {
    User invitee = entry.getUser();
    User inviter = entry.getInvitedBy() != null ? entry.getInvitedBy() : invitee;

    for (Event event : eventRepository.findByTrip_Id(entry.getTrip().getId())) {
      UserEvent participation =
          userEventRepository
              .findByEvent_IdAndUser_Id(event.getId(), invitee.getId())
              .orElseGet(() -> new UserEvent(event, invitee, inviter));
      participation.setStatus(UserEvent.InviteStatus.ACCEPTED);
      userEventRepository.save(participation);
    }
  }

  /**
   * Resolves the authenticated user by username or email.
   *
   * @param username username or email from the security context
   * @return the matching user
   */
  private User getCurrentUserOrThrow(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
  }

  /**
   * Loads a user by id.
   *
   * @param userId id of the user to load
   * @return the matching user
   */
  private User getUserOrThrow(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  /**
   * Loads a trip by id.
   *
   * @param tripId id of the trip to load
   * @return the matching trip
   */
  private Trip getTripOrThrow(long tripId) {
    return tripRepository
        .findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
  }

  /**
   * Checks whether the target user is already in the current user's friend set.
   *
   * @param currentUser user sending the invite
   * @param friend user that should receive the invite
   * @return {@code true} when both users are friends
   */
  private boolean isFriend(User currentUser, User friend) {
    return currentUser.getFriends().stream()
        .anyMatch(existingFriend -> existingFriend.getId() == friend.getId());
  }

  /**
   * Verifies that the user owns the trip before allowing invite management.
   *
   * @param trip trip whose owner should be checked
   * @param user user performing the action
   */
  private void requireOwner(Trip trip, User user) {
    if (trip.getOwner() == null || trip.getOwner().getId() != user.getId()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the trip creator can invite friends");
    }
  }
}
