package com.sep.sep_backend.tripManagement.dto;

/** WebSocket payload sent when a trip invitation is created or answered. */
public record TripInviteNotificationDTO(
    long tripId, String tripName, long userId, String username, String status) {}
