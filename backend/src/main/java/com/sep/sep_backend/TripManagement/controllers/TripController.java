package com.sep.sep_backend.TripManagement.controllers;

/* back-front relation: REST endpoints for trips.
GET /api/trips         → frontend trip-list page (loads all trips)
GET /api/trips/{id}    → frontend trip-detail page (loads one trip)
POST /api/trips        → frontend create-trip form */

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.TripManagement.dto.TripDTO;
import com.sep.sep_backend.TripManagement.services.TripService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class TripController {

  // Shape of the JSON body sent by the frontend when creating a trip
  public record CreateTripRequest(String name, LocalDate startDate, LocalDate endDate) {}

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @GetMapping
  public List<TripDTO> getAllTrips() {
    return tripService.getAllTrips().stream().map(TripDTO::from).toList();
  }

  @GetMapping("/{id}")
  public ResponseEntity<TripDTO> getTrip(@PathVariable Long id) {
    Optional<Trip> trip = tripService.getTrip(id);
    if (trip.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(TripDTO.from(trip.get()));
  }

  @PostMapping
  public ResponseEntity<TripDTO> createTrip(@RequestBody CreateTripRequest request) {
    try {
      Trip created = tripService.createTrip(request.name(), request.startDate(), request.endDate());
      return ResponseEntity.ok(TripDTO.from(created));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
    try {
      tripService.deleteTrip(id);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }
}
