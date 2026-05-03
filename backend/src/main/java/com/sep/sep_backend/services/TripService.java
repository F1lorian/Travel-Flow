package com.sep.sep_backend.services;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.TripRepository;
import org.springframework.stereotype.Service;

@Service
public class TripService {

    private final TripRepository tripRepository;

    // Constructor injection — Spring auto-provides the repository
    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    // Creates and saves a new trip with the given name
    public Trip createTrip(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Trip name cannot be empty");
        }
        Trip trip = new Trip(name);
        return tripRepository.save(trip);
    }
}
