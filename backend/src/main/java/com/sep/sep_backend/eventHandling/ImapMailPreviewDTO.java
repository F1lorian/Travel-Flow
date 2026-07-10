package com.sep.sep_backend.eventHandling;

/** Preview data for one message in an IMAP mailbox. */
public record ImapMailPreviewDTO(
    long uid, String subject, String from, String sentDate, String receivedDate, String snippet) {}
