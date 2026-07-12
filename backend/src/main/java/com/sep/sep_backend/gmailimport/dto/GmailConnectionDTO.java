package com.sep.sep_backend.gmailimport.dto;

/** Connection status for the current user's Gmail import. */
public record GmailConnectionDTO(boolean connected, String email) {}
