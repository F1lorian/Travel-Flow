package com.sep.sep_backend.accountmanagement;

/**
 * New password for {@code POST /api/account/reset-password-with-code}, authorised by the emailed
 * code instead of the security answer. The code expires 15 minutes after it was sent.
 */
public record ResetPasswordWithCodeRequestDTO(
    String username, String code, String newPassword, String repeatNewPassword) {}
