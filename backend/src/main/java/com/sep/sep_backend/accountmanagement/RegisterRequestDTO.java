package com.sep.sep_backend.accountmanagement;

/**
 * Sign-up data for {@code POST /api/account/register}.
 *
 * <p>Every field is required. {@code favoriteColor} is the answer to the fixed security question
 * and is stored BCrypt-hashed like the password, because it alone authorises a password reset.
 */
public record RegisterRequestDTO(
    String firstName,
    String surname,
    String username,
    String email,
    String password,
    String repeatPassword,
    String favoriteColor) {}
