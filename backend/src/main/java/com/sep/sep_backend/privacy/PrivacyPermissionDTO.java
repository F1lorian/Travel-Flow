package com.sep.sep_backend.privacy;

/**
 * DTO used to display privacy permissions for one friend.
 *
 * <p>The owner can see which friend has access to events or profile information.
 */
public record PrivacyPermissionDTO(
    long friendId,
    String username,
    String firstName,
    String surname,
    boolean canSeeEvents,
    boolean canSeeProfile) {}
