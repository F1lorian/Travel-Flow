package com.sep.sep_backend.friends;

public record FriendDTO(
    long id, String username, String firstName, String surname, boolean connected) {}
