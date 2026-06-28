package com.sep.sep_backend.profilemanagement;

/** Read-only privacy details shown to the logged-in user. */
public record ProfileDetailsDTO(
    String username,
    String firstName,
    String surname,
    String email,
    String provider,
    boolean emailVerified) {}
