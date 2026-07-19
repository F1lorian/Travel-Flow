package com.sep.sep_backend.tripManagement.dto;

import com.sep.sep_backend.entities.UserTrip;

/** DTO for one user participating in, or invited to, a trip. */
public record TripParticipantDTO(
    long userId, String username, String firstname, String surname, UserTrip.InviteStatus status) {

  /** Builds a participant DTO from the trip membership row. */
  public static TripParticipantDTO from(UserTrip userTrip) {
    return new TripParticipantDTO(
        userTrip.getUser().getId(),
        userTrip.getUser().getName(),
        userTrip.getUser().getFirstName(),
        userTrip.getUser().getSurname(),
        userTrip.getStatus());
  }
}
