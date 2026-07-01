package com.sep.sep_backend.gmailimport.dto;

/**
 * The Google consent-screen URL the frontend must redirect the user to in order to grant Gmail
 * access.
 *
 * @param authorizationUrl fully built Google OAuth 2.0 authorization URL
 */
public record GmailAuthUrlDTO(String authorizationUrl) {}
