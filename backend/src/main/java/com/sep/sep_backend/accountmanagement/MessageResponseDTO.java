package com.sep.sep_backend.accountmanagement;

/**
 * A bare human-readable message, used by the account endpoints that return no data — email
 * verification and the password-reset steps — and as the 400 error body.
 */
public record MessageResponseDTO(String message) {}
