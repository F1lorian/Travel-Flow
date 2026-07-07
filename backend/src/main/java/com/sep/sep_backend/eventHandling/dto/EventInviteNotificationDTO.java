package com.sep.sep_backend.eventHandling.dto;

public record EventInviteNotificationDTO(
        long eventId, String eventName, long userId, String username, String status) {}