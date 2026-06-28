package com.sep.sep_backend.profilemanagement;

/**
 * Request to change the logged-in user's password. The change must be authorised by verifying the
 * account email either with a one-time {@code code} (sent by e-mail) or with a Google {@code
 * googleIdToken}. Exactly one of the two should be provided.
 */
public record ChangePasswordRequestDTO(
    String newPassword, String repeatNewPassword, String code, String googleIdToken) {}
