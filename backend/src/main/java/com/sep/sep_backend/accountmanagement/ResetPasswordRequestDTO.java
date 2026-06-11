package com.sep.sep_backend.accountmanagement;

public record ResetPasswordRequestDTO(
    String username, String favoriteColor, String newPassword, String repeatNewPassword) {}
