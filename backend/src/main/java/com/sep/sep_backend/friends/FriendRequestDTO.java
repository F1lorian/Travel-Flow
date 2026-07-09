package com.sep.sep_backend.friends;

public record FriendRequestDTO(
    long id,
    long senderId,
    String senderUsername,
    long receiverId,
    String receiverUsername,
    FriendRequestStatus status) {}
