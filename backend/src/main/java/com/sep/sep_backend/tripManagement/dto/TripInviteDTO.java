package com.sep.sep_backend.tripManagement.dto;

import com.sep.sep_backend.entities.UserTrip;

/**
 * DTO used for the invites to trips
 * @param tripId
 * @param tripName
 * @param invitedByUserId
 * @param invitedByUsername
 * @param status can be ACCEPTED, PENDING, DECLINED
 */
public record TripInviteDTO(
        long tripId,
        String tripName,
        long invitedByUserId,
        String invitedByUsername,
        UserTrip.InviteStatus status) {

    public static TripInviteDTO from(UserTrip userTrip) {
        return new TripInviteDTO(
                userTrip.getTrip().getId(),
                userTrip.getTrip().getName(),
                userTrip.getInvitedBy() != null ? userTrip.getInvitedBy().getId() : 0,
                userTrip.getInvitedBy() != null ? userTrip.getInvitedBy().getName() : null,
                userTrip.getStatus());
    }
}