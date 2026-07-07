package com.sep.sep_backend.eventHandling.dto;

import com.sep.sep_backend.entities.UserEvent;

/**
 * DTO for EventInvitations
 * @param eventId
 * @param eventName
 * @param invitedByUserId
 * @param invitedByUsername
 * @param status
 */
public record EventInviteDTO(
        long eventId,
        String eventName,
        long invitedByUserId,
        String invitedByUsername,
        UserEvent.InviteStatus status) {

    public static EventInviteDTO from(UserEvent userEvent) {
        return new EventInviteDTO(
                userEvent.getEvent().getId(),
                userEvent.getEvent().getName(),
                userEvent.getCreator().getId(),
                userEvent.getCreator().getName(),
                userEvent.getStatus());
    }
}