package com.sep.sep_backend.accountmanagement;

public record RegisterRequestDTO(
    String firstName,
    String surname,
    String username,
    String email,
    String password,
    String repeatPassword,
    String favoriteColor) {}
