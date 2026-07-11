package com.sep.sep_backend.tripManagement.controllers;

/* back-front relation: REST endpoints for trips.
GET /api/trips         → frontend trip-list page (loads all trips)
GET /api/trips/{id}    → frontend trip-detail page (loads one trip)
POST /api/trips        → frontend create-trip form */

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripDTO;
import com.sep.sep_backend.tripManagement.services.TripService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for trips: list, fetch, create, and delete. Maps HTTP requests to {@link
 * TripService} and converts entities to {@link TripDTO}s so JPA entities aren't serialized directly
 * to the client.
 */
@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class TripController {

  // Shape of the JSON body sent by the frontend when creating a trip
  public record CreateTripRequest(String name, LocalDate startDate, LocalDate endDate) {}

  private final TripService tripService;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;

  public TripController(
      TripService tripService,
      UserRepository userRepository,
      UserTripRepository userTripRepository) {
    this.tripService = tripService;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
  }

  // GET /api/trips — all trips, each mapped to a DTO so entities aren't serialized directly.
  @GetMapping
  public List<TripDTO> getAllTrips(Authentication authentication) {
    Long userId = getCurrentUserId(authentication);
    return tripService.getAllTrips(userId).stream()
        .map(
            trip ->
                TripDTO.from(
                    trip,
                    userTripRepository
                        .findByIdUserIdAndIdTripId(userId, trip.getId())
                        .orElse(null)))
        .toList();
  }

  // GET /api/trips/{id} — one trip, or 404 if the id doesn't exist.
  @GetMapping("/{id}")
  public ResponseEntity<TripDTO> getTrip(@PathVariable Long id) {
    Optional<Trip> trip = tripService.getTrip(id);
    if (trip.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(TripDTO.from(trip.get()));
  }

  // POST /api/trips — create a trip. The service throws IllegalArgumentException
  // on invalid input (e.g. blank name), which we translate to HTTP 400.
  @PostMapping
  public ResponseEntity<TripDTO> createTrip(
      @RequestBody CreateTripRequest request, Authentication authentication) {
    try {
      Long userId = getCurrentUserId(authentication);
      Trip created =
          tripService.createTrip(request.name(), request.startDate(), request.endDate(), userId);
      return ResponseEntity.ok(TripDTO.from(created));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  // DELETE /api/trips/{id} — 204 No Content on success, 404 if the trip is missing.
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
    try {
      tripService.deleteTrip(id);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  @DeleteMapping("/{tripId}/friends/{friendUserId}")
  public ResponseEntity<Void> removeFriendFromTrip(
      @PathVariable Long tripId, @PathVariable Long friendUserId, Authentication authentication) {
    try {
      tripService.removeFriendFromTrip(tripId, friendUserId, getCurrentUserId(authentication));
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * returns the userid from the user currently logged in
   *
   * @param authentication
   * @return
   */
  private Long getCurrentUserId(Authentication authentication) {
    User user =
        userRepository
            .findByNameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), "")
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return user.getId();
  }
}
