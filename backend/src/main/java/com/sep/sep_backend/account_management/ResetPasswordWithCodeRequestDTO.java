package com.sep.sep_backend.account_management;

public record ResetPasswordWithCodeRequestDTO(
    String username, String code, String newPassword, String repeatNewPassword) {}
