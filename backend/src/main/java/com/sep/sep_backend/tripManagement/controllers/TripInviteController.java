package com.sep.sep_backend.tripManagement.controllers;

import com.sep.sep_backend.tripManagement.dto.TripInviteDTO;
import com.sep.sep_backend.tripManagement.dto.TripInviteResponseDTO;
import com.sep.sep_backend.tripManagement.services.TripInviteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoint for inviting a friend to a trip and for responding to (accepting/declining) an
 * incoming invite. The service checks that the inviter has accepted membership on the trip and
 * that the invitee really is a friend; failures surface as {@code {"message": "..."}} responses
 * via {@link com.sep.sep_backend.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/TripInvites")
public class TripInviteController {
  private final TripInviteService tripInviteService;

  public TripInviteController(TripInviteService tripInviteService) {
    this.tripInviteService = tripInviteService;
  }

  @PostMapping("/{tripId}/{friendId}")
  public TripInviteResponseDTO inviteFriendToTrip(
      @PathVariable long tripId, @PathVariable long friendId, Authentication authentication) {
    return tripInviteService.inviteFriendToTrip(
        getCurrentUsername(authentication), tripId, friendId);
  }

  @PostMapping("/{tripId}/accept")
  public TripInviteResponseDTO acceptTripInvite(
      @PathVariable long tripId, Authentication authentication) {
    return tripInviteService.respondToTripInvite(getCurrentUsername(authentication), tripId, true);
  }

  @PostMapping("/{tripId}/decline")
  public TripInviteResponseDTO declineTripInvite(
      @PathVariable long tripId, Authentication authentication) {
    return tripInviteService.respondToTripInvite(getCurrentUsername(authentication), tripId, false);
  }

  @GetMapping("/incoming")
  public List<TripInviteDTO> getIncomingTripInvites(Authentication authentication) {
    return tripInviteService.getIncomingTripInvites(getCurrentUsername(authentication)).stream()
        .map(TripInviteDTO::from)
        .toList();
  }

  private String getCurrentUsername(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    return authentication.getName();
  }
}
