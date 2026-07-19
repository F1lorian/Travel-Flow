package com.sep.sep_backend.friends;

/** User summary returned by friend lists and friend search results. */
public record FriendDTO(
    long id, String username, String firstName, String surname, boolean connected) {}
