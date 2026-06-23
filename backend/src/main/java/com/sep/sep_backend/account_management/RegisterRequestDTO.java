package com.sep.sep_backend.account_management;

public record RegisterRequestDTO(
    String firstName,
    String surname,
    String username,
    String email,
    String password,
    String repeatPassword,
    String favoriteColor) {}
