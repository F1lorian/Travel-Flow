package com.sep.sep_backend.TripManagement.services;

/* back-front relation: TripService handles all trip business logic.
Called by TripController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for trips: listing, fetching, creating, and deleting a trip together with its
 * events. Sits between {@link com.sep.sep_backend.TripManagement.controllers.TripController} and
 * the JPA repositories.
 */
@Service
public class TripService {

  private final TripRepository tripRepository;
  private final EventRepository eventRepository;
  private final UserRepository userRepository;

  public TripService(
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserRepository userRepository) {
    this.tripRepository = tripRepository;
    this.eventRepository = eventRepository;
    this.userRepository = userRepository;
  }

  public List<Trip> getAllTrips(Long userId) {
    return tripRepository.findAllByUserId(userId);
  }

  public Optional<Trip> getTrip(Long id) {
    return tripRepository.findById(id);
  }

  // Creates a trip. Name is required; dates are optional. Throws if the name is blank.
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
    user.getTrips().add(savedTrip);
    userRepository.save(user);

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
    List<Event> events = eventRepository.findByTrip_Id(id);
    eventRepository.deleteAll(events);
    tripRepository.deleteById(id);
  }
}
