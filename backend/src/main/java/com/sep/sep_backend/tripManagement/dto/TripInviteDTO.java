package com.sep.sep_backend.tripManagement.dto;

import com.sep.sep_backend.entities.UserTrip;

/**
 * DTO returned for a pending trip invitation.
 *
 * @param tripId invited trip identifier
 * @param tripName invited trip name
 * @param invitedByUserId user id of the inviter, or {@code 0} when no inviter is stored
 * @param invitedByUsername username of the inviter, or {@code null} when no inviter is stored
 * @param status current invitation status
 */
public record TripInviteDTO(
    long tripId,
    String tripName,
    long invitedByUserId,
    String invitedByUsername,
    UserTrip.InviteStatus status) {

  /** Builds an invite DTO from the user-trip membership row. */
  public static TripInviteDTO from(UserTrip userTrip) {
    return new TripInviteDTO(
        userTrip.getTrip().getId(),
        userTrip.getTrip().getName(),
        userTrip.getInvitedBy() != null ? userTrip.getInvitedBy().getId() : 0,
        userTrip.getInvitedBy() != null ? userTrip.getInvitedBy().getName() : null,
        userTrip.getStatus());
  }
}
