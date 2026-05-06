package com.sep.sep_backend.TripManagement.services;

/* back-front relation: TripService handles all trip business logic.
   Called by TripController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.TripRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TripService {

    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
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
}