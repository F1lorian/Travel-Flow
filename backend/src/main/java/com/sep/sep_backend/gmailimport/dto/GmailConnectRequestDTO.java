package com.sep.sep_backend.gmailimport.dto;

/** Request carrying the OAuth authorization code returned to the frontend redirect. */
public record GmailConnectRequestDTO(String code) {}
