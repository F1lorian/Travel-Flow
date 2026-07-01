package com.sep.sep_backend.gmailimport.dto;

/**
 * Whether the current user has connected a Gmail account, and if so which address.
 *
 * @param connected {@code true} if a Gmail account is linked and usable
 * @param emailAddress connected Gmail address, or {@code null} when not connected
 */
public record GmailConnectionStatusDTO(boolean connected, String emailAddress) {}
