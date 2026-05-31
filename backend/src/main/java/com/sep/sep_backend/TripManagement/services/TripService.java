package com.sep.sep_backend.TripManagement.services;

/* back-front relation: TripService handles all trip business logic.
Called by TripController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
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

  public TripService(TripRepository tripRepository, EventRepository eventRepository) {
    this.tripRepository = tripRepository;
    this.eventRepository = eventRepository;
  }

  public List<Trip> getAllTrips() {
    return tripRepository.findAll();
  }

  public Optional<Trip> getTrip(Long id) {
    return tripRepository.findById(id);
  }

  // Creates a trip. Name is required; dates are optional. Throws if the name is blank.
  public Trip createTrip(String name, LocalDate startDate, LocalDate endDate) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Trip name cannot be empty");
    }
    Trip trip = new Trip(name);
    trip.setStartDate(startDate);
    trip.setEndDate(endDate);
    return tripRepository.save(trip);
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
