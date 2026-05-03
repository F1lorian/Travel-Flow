package com.sep.sep_backend.controllers;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.services.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*") // allow frontend to call this from another port
public class TripController {

    private final TripService tripService;
    private final TripRepository tripRepository;

    public TripController(TripService tripService, TripRepository tripRepository) {
        this.tripService = tripService;
        this.tripRepository = tripRepository;
    }

    // POST /api/trips → create a new trip
    @PostMapping
    public ResponseEntity<Trip> createTrip(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        Trip trip = tripService.createTrip(name);
        return ResponseEntity.ok(trip);
    }

    // GET /api/trips → list all trips
    @GetMapping
    public List<Trip> getAllTrips() {
        return tripRepository.findAll();
    }

    // GET /api/trips/{id} → fetch one trip by ID
    @GetMapping("/{id}")
    public ResponseEntity<Trip> getTrip(@PathVariable Long id) {
        return tripRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}