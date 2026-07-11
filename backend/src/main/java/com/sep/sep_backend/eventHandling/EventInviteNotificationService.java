package com.sep.sep_backend.eventHandling;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.eventHandling.dto.EventInviteNotificationDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Service for the notifications used for the connection via websockets */
@Service
public class EventInviteNotificationService {

  private final SimpMessagingTemplate messagingTemplate;

  public EventInviteNotificationService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void notifyNewInvite(User invitee, Event event, User invitedBy) {
    messagingTemplate.convertAndSendToUser(
        invitee.getName(),
        "/queue/event-invites",
        new EventInviteNotificationDTO(
            event.getId(), event.getName(), invitedBy.getId(), invitedBy.getName(), "PENDING"));
  }

  public void notifyInviteAnswered(UserEvent userEvent) {
    messagingTemplate.convertAndSendToUser(
        userEvent.getCreator().getName(),
        "/queue/event-invites/status",
        new EventInviteNotificationDTO(
            userEvent.getEvent().getId(),
            userEvent.getEvent().getName(),
            userEvent.getUser().getId(),
            userEvent.getUser().getName(),
            userEvent.getStatus().name()));
  }
}
