package com.sep.sep_backend.friends;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class FriendRequestNotificationService {

  private final SimpMessagingTemplate messagingTemplate;

  public FriendRequestNotificationService(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void notifyNewRequest(FriendRequestDTO dto) {
    messagingTemplate.convertAndSendToUser(dto.receiverUsername(), "/queue/friend-requests", dto);
  }

  public void notifyRequestAnswered(FriendRequestDTO dto) {
    messagingTemplate.convertAndSendToUser(
        dto.senderUsername(), "/queue/friend-requests/status", dto);
  }

  public void notifyFriendRemoved(String affectedUsername, long removedByUserId) {
    messagingTemplate.convertAndSendToUser(
        affectedUsername, "/queue/friend-removed", removedByUserId);
  }
}
