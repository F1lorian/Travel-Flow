package com.sep.sep_backend.tripManagement.dto;

/** Response returned after a trip invitation is sent, accepted, or declined. */
public record TripInviteResponseDTO(long tripId, long friendId, String message) {}
