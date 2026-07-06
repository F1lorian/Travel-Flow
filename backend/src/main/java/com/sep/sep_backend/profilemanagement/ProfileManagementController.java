package com.sep.sep_backend.profilemanagement;

import com.sep.sep_backend.accountmanagement.MessageResponseDTO;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service profile endpoints for the logged-in user. All routes require a valid JWT (they are
 * outside the public {@code /api/account/**} matcher), so the principal identifies the account.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileManagementController {
  private final ProfileManagementService profileManagementService;

  public ProfileManagementController(ProfileManagementService profileManagementService) {
    this.profileManagementService = profileManagementService;
  }

  /** Returns the logged-in user's privacy details. */
  @GetMapping
  public ProfileDetailsDTO getProfile(Principal principal) {
    return profileManagementService.getProfile(currentUsername(principal));
  }

  /** Emails the logged-in user a one-time code used to authorise a password change or deletion. */
  @PostMapping("/send-change-code")
  public MessageResponseDTO sendChangeCode(Principal principal) {
    return profileManagementService.sendChangeCode(currentUsername(principal));
  }

  /** Changes the logged-in user's password after verifying ownership by code or Google sign-in. */
  @PostMapping("/change-password")
  public MessageResponseDTO changePassword(
      Principal principal, @RequestBody ChangePasswordRequestDTO request) {
    return profileManagementService.changePassword(currentUsername(principal), request);
  }

  /** Permanently deletes the logged-in user after verifying ownership by code or Google sign-in. */
  @DeleteMapping
  public MessageResponseDTO deleteAccount(
      Principal principal, @RequestBody DeleteAccountRequestDTO request) {
    return profileManagementService.deleteAccount(currentUsername(principal), request);
  }

  // Validation errors (IllegalArgumentException) become 400 {"message": ...} responses through
  // the application-wide GlobalExceptionHandler.
  private String currentUsername(Principal principal) {
    if (principal == null) {
      throw new IllegalArgumentException("Not authenticated");
    }
    return principal.getName();
  }
}
