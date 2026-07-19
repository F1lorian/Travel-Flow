package com.sep.sep_backend.friends;

/** API and WebSocket representation of a friend request and its current status. */
public record FriendRequestDTO(
    long id,
    long senderId,
    String senderUsername,
    long receiverId,
    String receiverUsername,
    FriendRequestStatus status) {}
