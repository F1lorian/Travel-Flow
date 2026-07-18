package com.sep.sep_backend.accountmanagement;

/**
 * Confirms an account for {@code POST /api/account/verify-email} with the six-digit code sent at
 * registration.
 */
public record VerifyEmailRequestDTO(String email, String code) {}
