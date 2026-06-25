package com.sep.sep_backend.profilemanagement;

/**
 * Request to permanently delete the logged-in user's account. The deletion must be authorised by
 * verifying the account email either with a one-time {@code code} (sent by e-mail) or with a Google
 * {@code googleIdToken}. Exactly one of the two should be provided.
 */
public record DeleteAccountRequestDTO(String code, String googleIdToken) {}
