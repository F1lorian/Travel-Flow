package com.sep.sep_backend.friends;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Sends user-specific WebSocket notifications for friend request changes. */
@Service
public class FriendRequestNotificationService {

  private final SimpMessagingTemplate messagingTemplate;

  public FriendRequestNotificationService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  /** Notifies the receiver that a new friend request is waiting for them. */
  public void notifyNewRequest(FriendRequestDTO dto) {
    messagingTemplate.convertAndSendToUser(dto.receiverUsername(), "/queue/friend-requests", dto);
  }

  /** Notifies the sender that their friend request was accepted or declined. */
  public void notifyRequestAnswered(FriendRequestDTO dto) {
    messagingTemplate.convertAndSendToUser(
        dto.senderUsername(), "/queue/friend-requests/status", dto);
  }

  /** Notifies a user that another user removed the friendship connection. */
  public void notifyFriendRemoved(String affectedUsername, long removedByUserId) {
    messagingTemplate.convertAndSendToUser(
        affectedUsername, "/queue/friend-removed", removedByUserId);
  }
}
