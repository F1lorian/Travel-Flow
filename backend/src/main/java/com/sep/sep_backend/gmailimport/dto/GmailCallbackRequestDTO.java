package com.sep.sep_backend.gmailimport.dto;

/**
 * Payload the frontend posts after Google redirects back with an authorization code.
 *
 * @param code one-time authorization code issued by Google
 */
public record GmailCallbackRequestDTO(String code) {}
