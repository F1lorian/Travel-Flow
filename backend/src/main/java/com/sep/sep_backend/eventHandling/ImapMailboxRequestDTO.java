package com.sep.sep_backend.eventHandling;

/** Request data for reading a limited preview list from an IMAP mailbox. */
public record ImapMailboxRequestDTO(
    String host, Integer port, String username, String password, String folder, Integer limit) {}
