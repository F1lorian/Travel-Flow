package com.sep.sep_backend.accountmanagement;

/**
 * New password for {@code POST /api/account/reset-password}, authorised by the security answer.
 *
 * <p>The answer is re-checked here rather than trusted from the preceding {@code /forgot-password}
 * call, since that call issues no token and the two requests are otherwise unlinked.
 */
public record ResetPasswordRequestDTO(
    String username, String favoriteColor, String newPassword, String repeatNewPassword) {}
