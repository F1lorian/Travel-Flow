package com.sep.sep_backend.privacy;

/**
 * DTO for profile information that can be shared with friends.
 *
 * <p>Sensitive information such as password hash, security answers or verification codes is never
 * included.
 */
public record SharedUserProfileDTO(long id, String username, String firstName, String surname) {}
