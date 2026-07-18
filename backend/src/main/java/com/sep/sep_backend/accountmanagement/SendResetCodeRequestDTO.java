package com.sep.sep_backend.accountmanagement;

/**
 * Requests a reset code by email via {@code POST /api/account/send-reset-code}, the first step of
 * the code-based reset path. Accepts a username or an email.
 */
public record SendResetCodeRequestDTO(String username) {}
