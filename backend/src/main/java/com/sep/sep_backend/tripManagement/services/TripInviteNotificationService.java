package com.sep.sep_backend.tripManagement.services;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.tripManagement.dto.TripInviteNotificationDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Service for the notifications used for the connection via websockets */
@Service
public class TripInviteNotificationService {

  private final SimpMessagingTemplate messagingTemplate;

  public TripInviteNotificationService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void notifyNewInvite(User invitee, Trip trip, User invitedBy) {
    messagingTemplate.convertAndSendToUser(
        invitee.getName(),
        "/queue/trip-invites",
        new TripInviteNotificationDTO(
            trip.getId(), trip.getName(), invitedBy.getId(), invitedBy.getName(), "PENDING"));
  }

  public void notifyInviteAnswered(UserTrip entry) {
    messagingTemplate.convertAndSendToUser(
        entry.getInvitedBy().getName(),
        "/queue/trip-invites/status",
        new TripInviteNotificationDTO(
            entry.getTrip().getId(),
            entry.getTrip().getName(),
            entry.getUser().getId(),
            entry.getUser().getName(),
            entry.getStatus().name()));
  }
}
