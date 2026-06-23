package com.sep.sep_backend.account_management;

public record ResetPasswordRequestDTO(
    String username, String favoriteColor, String newPassword, String repeatNewPassword) {}
