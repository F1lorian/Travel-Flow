package com.sep.sep_backend.gmailimport.dto;

/** Request identifying which connected-mailbox message should be parsed into an event. */
public record GmailParseRequestDTO(String messageId) {}
