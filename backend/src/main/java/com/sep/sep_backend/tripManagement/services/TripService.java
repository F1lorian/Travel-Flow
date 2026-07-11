package com.sep.sep_backend.tripManagement.services;

/* back-front relation: TripService handles all trip business logic.
Called by TripController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.entities.UserTripId;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripParticipantDTO;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business logic for trips: listing, fetching, creating, and deleting a trip together with its
 * events. Sits between {@link com.sep.sep_backend.tripManagement.controllers.TripController} and
 * the JPA repositories.
 *
 * <p>Every read or write of a single trip is guarded by a membership check, so a user can never see
 * or delete another user's trip just by guessing its id.
 */
@Service
public class TripService {

  private final TripRepository tripRepository;
  private final EventRepository eventRepository;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;
  private final UserEventRepository userEventRepository;
  private final AusgabeRepository ausgabeRepository;
  private final EmailNotificationService emailNotificationService;

  public TripService(
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository,
      AusgabeRepository ausgabeRepository,
      EmailNotificationService emailNotificationService) {
    this.tripRepository = tripRepository;
    this.eventRepository = eventRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.userEventRepository = userEventRepository;
    this.ausgabeRepository = ausgabeRepository;
    this.emailNotificationService = emailNotificationService;
  }

  /** Returns the trips the given user has accepted or pending membership on. */
  public List<Trip> getAllTrips(Long userId) {
    return tripRepository.findAllByUserId(userId);
  }

  /**
   * Returns a trip if it exists (404 otherwise) and the given user has accepted membership (403
   * otherwise). All trip-scoped operations go through this so access rules live in one place.
   */
  public Trip getTripForMember(Long tripId, Long userId) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + tripId));
    requireMember(tripId, userId);
    return trip;
  }

  /**
   * Rejects users without an accepted membership on the trip, so trip ids cannot simply be guessed
   * and a still-pending invite doesn't grant full access.
   */
  public void requireMember(Long tripId, Long userId) {
    boolean isAcceptedMember =
        userTripRepository
            .findByIdUserIdAndIdTripId(userId, tripId)
            .filter(entry -> entry.getStatus() == UserTrip.InviteStatus.ACCEPTED)
            .isPresent();
    if (!isAcceptedMember) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip");
    }
  }

  /**
   * Creates a trip and makes the creating user its first, already-accepted member. The name is
   * required and, when both dates are given, the trip may not end before it starts; violations
   * become readable 400 responses for the frontend.
   */
  public Trip createTrip(String name, LocalDate startDate, LocalDate endDate, Long userId) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Trip name cannot be empty");
    }
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("Trip end date cannot be before its start date");
    }

    // Resolve the creator before saving anything so a bad user id cannot leave an orphaned trip.
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    Trip trip = new Trip(name);
    trip.setStartDate(startDate);
    trip.setEndDate(endDate);
    trip.setOwner(user);
    Trip savedTrip = tripRepository.save(trip);

    userTripRepository.save(new UserTrip(user, savedTrip, null, UserTrip.InviteStatus.ACCEPTED));

    // Mirror the in-app confirmation by email; a mail failure never fails the creation itself.
    emailNotificationService.sendBestEffort(
        user.getEmail(),
        "Your trip \"" + savedTrip.getName() + "\" was created",
        "Your trip \"" + savedTrip.getName() + "\" was created. Log in to SEP to plan its events.");

    return savedTrip;
  }

  /**
   * Deletes a trip and all of its events, expenses, and memberships. Only accepted members may
   * delete a trip. @Transactional makes all deletions succeed or fail together, so a trip is never
   * left with orphaned events.
   */
  @Transactional
  public void deleteTrip(Long id, Long userId) {
    // The name is captured up front so the confirmation email can still mention it afterwards.
    Trip trip =
        tripRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + id));
    requireMember(id, userId);

    if (trip.getOwner() == null || trip.getOwner().getId() != userId) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the trip creator can delete this trip");
    }
    userEventRepository.deleteByEvent_Trip_Id(id);
    eventRepository.deleteByTrip_Id(id);
    ausgabeRepository.deleteByTrip_Id(id);
    userTripRepository.deleteByIdTripId(id);
    tripRepository.deleteById(id);

    // Mirror the in-app notification by email, best-effort (never fails the deletion).
    userRepository
        .findById(userId)
        .ifPresent(
            user ->
                emailNotificationService.sendBestEffort(
                    user.getEmail(),
                    "Your trip \"" + trip.getName() + "\" was deleted",
                    "Your trip \""
                        + trip.getName()
                        + "\" and all of its events and expenses were deleted."));
  }

  public List<TripParticipantDTO> getParticipants(Long tripId) {
    return userTripRepository.findByTrip_Id(tripId).stream().map(TripParticipantDTO::from).toList();
  }

  /**
   * Removes a friend from a trip and from all events belonging to that trip. The friendship itself
   * is not changed.
   */
  @Transactional
  public void removeFriendFromTrip(Long tripId, Long friendUserId, Long requestingUserId) {
    if (!tripRepository.existsById(tripId)) {
      throw new IllegalArgumentException("Trip not found: " + tripId);
    }

    UserTrip requesterTrip =
        userTripRepository
            .findByIdUserIdAndIdTripId(requestingUserId, tripId)
            .orElseThrow(() -> new IllegalArgumentException("User is not part of this trip"));
    if (requesterTrip.getStatus() != UserTrip.InviteStatus.ACCEPTED) {
      throw new IllegalArgumentException("Only accepted trip members can remove friends");
    }

    userTripRepository
        .findByIdUserIdAndIdTripId(friendUserId, tripId)
        .orElseThrow(() -> new IllegalArgumentException("Friend is not part of this trip"));

    userEventRepository.deleteByEvent_Trip_IdAndUser_Id(tripId, friendUserId);
    userTripRepository.deleteById(new UserTripId(friendUserId, tripId));
  }
}
