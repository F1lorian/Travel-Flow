package com.sep.sep_backend.TripManagement.services;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

  public List<Trip> getAllTripsForUser(String username) {
    return tripRepository.findTripsByUsername(username);
  }

  public boolean ownsTrip(String username, Long tripId) {
    return tripRepository.countByUsernameAndTripId(username, tripId) > 0;
  }

  public Optional<Trip> getTrip(Long id, String username) {
    if (!ownsTrip(username, id)) {
      return Optional.empty();
    }
    return tripRepository.findById(id);
  }

  @Transactional
  public Trip createTrip(String name, LocalDate startDate, LocalDate endDate, String username) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Trip name cannot be empty");
    }
    User user =
        userRepository
            .findByName(username)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

    Trip trip = new Trip(name);
    trip.setStartDate(startDate);
    trip.setEndDate(endDate);
    Trip savedTrip = tripRepository.save(trip);

    List<Trip> trips = user.getTrips();
    if (trips == null) {
      user.setTrips(new ArrayList<>());
    }
    user.getTrips().add(savedTrip);
    userRepository.save(user);

    return savedTrip;
  }

  @Transactional
  public void deleteTrip(Long id, String username) {
    if (!ownsTrip(username, id)) {
      throw new IllegalArgumentException("Trip not found: " + id);
    }
    List<Event> events = eventRepository.findByTrip_Id(id);
    eventRepository.deleteAll(events);
    tripRepository.deleteById(id);
  }
}
