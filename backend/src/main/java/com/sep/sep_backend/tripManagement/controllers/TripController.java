package com.sep.sep_backend.tripManagement.controllers;

/* back-front relation: REST endpoints for trips.
GET    /api/trips      → frontend trip-list page (loads the user's trips)
GET    /api/trips/{id} → frontend trip-detail page (loads one trip)
POST   /api/trips      → frontend create-trip form
DELETE /api/trips/{id} → frontend delete-trip button */

import com.sep.sep_backend.accountmanagement.CurrentUserService;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import com.sep.sep_backend.tripManagement.dto.TripDTO;
import com.sep.sep_backend.tripManagement.dto.TripParticipantDTO;
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
 *
 * <p>Errors are not handled here: validation problems and access violations thrown by the service
 * bubble up to {@link com.sep.sep_backend.exception.GlobalExceptionHandler}, which turns them into
 * {@code {"message": "..."}} responses the frontend shows to the user.
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

  private final TripRepository tripRepository;

  /** Shape of the JSON body sent by the frontend when creating a trip. */
  public record CreateTripRequest(String name, LocalDate startDate, LocalDate endDate) {}

  private final TripService tripService;
  private final UserRepository userRepository;
  private final UserTripRepository userTripRepository;
  private final CurrentUserService currentUserService;

  public TripController(
      TripService tripService,
      UserRepository userRepository,
      UserTripRepository userTripRepository,
      TripRepository tripRepository,
      CurrentUserService currentUserService) {
    this.tripService = tripService;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
    this.tripRepository = tripRepository;
    this.currentUserService = currentUserService;
  }

  /** GET /api/trips — the logged-in user's trips, each with its invite info when present. */
  @GetMapping
  public List<TripDTO> getAllTrips(Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    return tripService.getAllTrips(userId).stream()
        .map(trip -> toDtoWithMembership(trip, userId))
        .toList();
  }

  /** GET /api/trips/{id} — one trip; 404 if unknown, 403 if the user is not a member. */
  @GetMapping("/{id}")
  public TripDTO getTrip(@PathVariable Long id, Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    Trip trip = tripService.getTripForMember(id, userId);
    return toDtoWithMembership(trip, userId);
  }

  /** POST /api/trips — create a trip; invalid input becomes 400 with the reason as message. */
  @PostMapping
  public TripDTO createTrip(@RequestBody CreateTripRequest request, Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    Trip created =
        tripService.createTrip(request.name(), request.startDate(), request.endDate(), userId);
    return TripDTO.from(created);
  }

  /** DELETE /api/trips/{id} — 204 on success; 404 if unknown, 403 if not a member. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTrip(@PathVariable Long id, Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    tripService.deleteTrip(id, userId);
    return ResponseEntity.noContent().build();
  }

  /** DELETE /api/trips/{tripId}/friends/{friendUserId} — remove a member from a trip. */
  @DeleteMapping("/{tripId}/friends/{friendUserId}")
  public ResponseEntity<Void> removeFriendFromTrip(
      @PathVariable Long tripId, @PathVariable Long friendUserId, Authentication authentication) {
    try {
      Long userId = currentUserService.requireUser(authentication).getId();
      tripService.removeFriendFromTrip(tripId, friendUserId, userId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /** Attaches the user's own membership row so the DTO carries who invited them, if anyone. */
  private TripDTO toDtoWithMembership(Trip trip, Long userId) {
    return TripDTO.from(
        trip, userTripRepository.findByIdUserIdAndIdTripId(userId, trip.getId()).orElse(null));
  }

  @GetMapping("/{id}/participants")
  public ResponseEntity<List<TripParticipantDTO>> getParticipants(@PathVariable Long id) {
    return ResponseEntity.ok(tripService.getParticipants(id));
  }
}
