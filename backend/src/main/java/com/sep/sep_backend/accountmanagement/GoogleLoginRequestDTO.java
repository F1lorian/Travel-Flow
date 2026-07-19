package com.sep.sep_backend.accountmanagement;

/** Request body for logging in with the Google ID token received by the frontend. */
public record GoogleLoginRequestDTO(String idToken) {}
