package com.sep.sep_backend.TripManagement.services;

/* back-front relation: TripService handles all trip business logic.
Called by TripController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  public Trip createTrip(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Trip name cannot be empty");
    }
    return tripRepository.save(new Trip(name));
  }

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