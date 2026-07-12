package com.sep.sep_backend.privacy;

/**
 * Request DTO for updating privacy permissions for one friend.
 *
 * <p>Both values can be changed at any time by the owner.
 */
public record UpdatePrivacyPermissionRequestDTO(boolean canSeeEvents, boolean canSeeProfile) {}
