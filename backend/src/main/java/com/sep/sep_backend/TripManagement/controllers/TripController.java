package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.TripManagement.dto.TripDTO;
import com.sep.sep_backend.TripManagement.services.TripService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class TripController {

  public record CreateTripRequest(String name, LocalDate startDate, LocalDate endDate) {}

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @GetMapping
  public List<TripDTO> getAllTrips(@AuthenticationPrincipal String username) {
    return tripService.getAllTripsForUser(username).stream().map(TripDTO::from).toList();
  }

  @GetMapping("/{id}")
  public ResponseEntity<TripDTO> getTrip(
      @PathVariable Long id, @AuthenticationPrincipal String username) {
    Optional<Trip> trip = tripService.getTrip(id, username);
    if (trip.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(TripDTO.from(trip.get()));
  }

  @PostMapping
  public ResponseEntity<TripDTO> createTrip(
      @RequestBody CreateTripRequest request, @AuthenticationPrincipal String username) {
    try {
      Trip created =
          tripService.createTrip(request.name(), request.startDate(), request.endDate(), username);
      return ResponseEntity.ok(TripDTO.from(created));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTrip(
      @PathVariable Long id, @AuthenticationPrincipal String username) {
    try {
      tripService.deleteTrip(id, username);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }
}
