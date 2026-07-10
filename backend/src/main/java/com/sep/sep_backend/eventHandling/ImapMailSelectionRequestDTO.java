package com.sep.sep_backend.eventHandling;

/** Request data for selecting a single IMAP message by UID. */
public record ImapMailSelectionRequestDTO(
    String host, Integer port, String username, String password, String folder, long uid) {}
