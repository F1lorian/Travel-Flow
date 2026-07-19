package com.sep.sep_backend.eventHandling.dto;

/** WebSocket payload sent when an event invitation is created or answered. */
public record EventInviteNotificationDTO(
    long eventId, String eventName, long userId, String username, String status) {}
