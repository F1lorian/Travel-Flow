package com.sep.sep_backend.tripManagement.dto;

public record TripInviteNotificationDTO(
    long tripId, String tripName, long userId, String username, String status) {}
