package com.sep.sep_backend.eventHandling.dto;

import com.sep.sep_backend.entities.UserEvent;

/**
 * DTO returned for a pending event invitation.
 *
 * @param eventId invited event identifier
 * @param eventName invited event name
 * @param invitedByUserId user id of the inviter
 * @param invitedByUsername username of the inviter
 * @param status current invitation status
 */
public record EventInviteDTO(
    long eventId,
    String eventName,
    long invitedByUserId,
    String invitedByUsername,
    UserEvent.InviteStatus status) {

  /** Builds an invite DTO from the user-event participation row. */
  public static EventInviteDTO from(UserEvent userEvent) {
    return new EventInviteDTO(
        userEvent.getEvent().getId(),
        userEvent.getEvent().getName(),
        userEvent.getCreator().getId(),
        userEvent.getCreator().getName(),
        userEvent.getStatus());
  }
}
