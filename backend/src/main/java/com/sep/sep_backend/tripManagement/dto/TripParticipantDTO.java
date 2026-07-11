package com.sep.sep_backend.tripManagement.dto;

import com.sep.sep_backend.entities.UserTrip;

public record TripParticipantDTO(
    long userId, String username, String firstname, String surname, UserTrip.InviteStatus status) {

  public static TripParticipantDTO from(UserTrip userTrip) {
    return new TripParticipantDTO(
        userTrip.getUser().getId(),
        userTrip.getUser().getName(),
        userTrip.getUser().getFirstName(),
        userTrip.getUser().getSurname(),
        userTrip.getStatus());
  }
}
