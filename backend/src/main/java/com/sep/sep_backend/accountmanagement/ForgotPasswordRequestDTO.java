package com.sep.sep_backend.accountmanagement;

/**
 * Security-answer check for {@code POST /api/account/forgot-password}, the first step of the
 * question-based reset path. Only confirms the answer — the new password is sent separately to
 * {@code /reset-password}.
 */
public record ForgotPasswordRequestDTO(String username, String favoriteColor) {}
