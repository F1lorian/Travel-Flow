package com.sep.sep_backend.accountmanagement;

public record ResetPasswordWithCodeRequestDTO(
    String username, String code, String newPassword, String repeatNewPassword) {}
