package com.sep.sep_backend.tripManagement.services;

/* back-front relation: TripService handles all trip business logic.
Called by TripController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for trips: listing, fetching, creating, and deleting a trip together with its
 * events. Sits between {@link com.sep.sep_backend.tripManagement.controllers.TripController} and
 * the JPA repositories.
 */
@Service
public class TripService {

  private final TripRepository tripRepository;
  private final EventRepository eventRepository;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;
  private final UserEventRepository userEventRepository;
  private final AusgabeRepository ausgabeRepository;

  public TripService(
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository,
      AusgabeRepository ausgabeRepository) {
    this.tripRepository = tripRepository;
    this.eventRepository = eventRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.userEventRepository = userEventRepository;
    this.ausgabeRepository = ausgabeRepository;
  }

  public List<Trip> getAllTrips(Long userId) {
    return tripRepository.findAllByUserId(userId);
  }

  public Optional<Trip> getTrip(Long id) {
    return tripRepository.findById(id);
  }

  // Creates a trip. Name is required; dates are optional. Throws if the name is blank. Status is
  // automatically set to ACCEPTED
  public Trip createTrip(String name, LocalDate startDate, LocalDate endDate, Long userId) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Trip name cannot be empty");
    }
    Trip trip = new Trip(name);
    trip.setStartDate(startDate);
    trip.setEndDate(endDate);
    Trip savedTrip = tripRepository.save(trip);

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    userTripRepository.save(new UserTrip(user, savedTrip, null, UserTrip.InviteStatus.ACCEPTED));

    return savedTrip;
  }

  /**
   * Deletes a trip and all of its events. @Transactional makes the event deletion and the trip
   * deletion succeed or fail together, so a trip is never left with orphaned events. Throws if the
   * trip doesn't exist.
   */
  @Transactional
  public void deleteTrip(Long id) {
    if (!tripRepository.existsById(id)) {
      throw new IllegalArgumentException("Trip not found: " + id);
    }

    userEventRepository.deleteByEvent_Trip_Id(id);
    eventRepository.deleteByTrip_Id(id);
    ausgabeRepository.deleteByTrip_Id(id);
    userTripRepository.deleteByIdTripId(id);
    tripRepository.deleteById(id);
  }
}
