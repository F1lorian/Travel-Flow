package com.sep.sep_backend.accountmanagement;

/** Credentials for {@code POST /api/account/login}; the username field also accepts an email. */
public record LoginRequestDTO(String username, String password) {}
