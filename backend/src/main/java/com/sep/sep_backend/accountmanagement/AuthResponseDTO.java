package com.sep.sep_backend.accountmanagement;

/**
 * Reply to a successful registration or login.
 *
 * <p>The token is null after registration: the account exists but cannot be used until the emailed
 * verification code has been confirmed, so there is nothing to authenticate with yet.
 */
public record AuthResponseDTO(String message, String username, String token) {}
