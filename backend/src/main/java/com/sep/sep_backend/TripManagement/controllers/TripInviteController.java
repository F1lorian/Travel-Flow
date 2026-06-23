package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.TripManagement.dto.TripInviteResponseDTO;
import com.sep.sep_backend.TripManagement.services.TripInviteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/TripInvites")
@CrossOrigin(origins = "*")
public class TripInviteController {
  private final TripInviteService tripInviteService;

  public TripInviteController(TripInviteService tripInviteService) {
    this.tripInviteService = tripInviteService;
  }

  @PostMapping("/{tripId}/{friendId}")
  public TripInviteResponseDTO inviteFriendToTrip(
      @PathVariable long tripId, @PathVariable long friendId, Authentication authentication) {
    return tripInviteService.inviteFriendToTrip(getCurrentUsername(authentication), tripId, friendId);
  }

  private String getCurrentUsername(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    return authentication.getName();
  }
}
