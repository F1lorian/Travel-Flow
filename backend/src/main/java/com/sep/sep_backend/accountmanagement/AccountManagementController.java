package com.sep.sep_backend.accountmanagement;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public entry points for registering, logging in and recovering an account.
 *
 * <p>The whole {@code /api/account/**} tree is permitted without a token by {@code SecurityConfig}
 * — it is where tokens come from. Nothing here may therefore assume an authenticated principal;
 * every endpoint identifies the user from the request body alone.
 */
@RestController
@RequestMapping("/api/account")
public class AccountManagementController {
  private final AccountManagementService accountManagementService;

  public AccountManagementController(AccountManagementService accountManagementService) {
    this.accountManagementService = accountManagementService;
  }

  /** Creates an unverified account and emails a verification code. Returns 201 without a token. */
  @PostMapping("/register")
  public ResponseEntity<AuthResponseDTO> register(@RequestBody RegisterRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(accountManagementService.register(request));
  }

  /** Exchanges username-or-email and password for a JWT. Requires a verified email. */
  @PostMapping("/login")
  public AuthResponseDTO login(@RequestBody LoginRequestDTO request) {
    return accountManagementService.login(request);
  }

  /**
   * Exchanges a Google ID token for a JWT, creating the account on first sign-in. Unrelated to the
   * Gmail import OAuth flow, which uses an authorization code and a client secret.
   */
  @PostMapping("/oauth/google")
  public AuthResponseDTO loginWithGoogle(@RequestBody GoogleLoginRequestDTO request) {
    return accountManagementService.loginWithGoogle(request);
  }

  /** Confirms the emailed registration code and unlocks login. */
  @PostMapping("/verify-email")
  public MessageResponseDTO verifyEmail(@RequestBody VerifyEmailRequestDTO request) {
    return accountManagementService.verifyEmail(request);
  }

  /** Checks the security answer. Step one of the question-based reset; changes nothing. */
  @PostMapping("/forgot-password")
  public MessageResponseDTO forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
    return accountManagementService.forgotPassword(request);
  }

  /** Emails a reset code valid for 15 minutes. Step one of the code-based reset. */
  @PostMapping("/send-reset-code")
  public MessageResponseDTO sendResetCode(@RequestBody SendResetCodeRequestDTO request) {
    return accountManagementService.sendResetCode(request);
  }

  /** Sets a new password using the emailed code. Step two of the code-based reset. */
  @PostMapping("/reset-password-with-code")
  public MessageResponseDTO resetPasswordWithCode(
      @RequestBody ResetPasswordWithCodeRequestDTO request) {
    return accountManagementService.resetPasswordWithCode(request);
  }

  /** Sets a new password using the security answer. Step two of the question-based reset. */
  @PostMapping("/reset-password")
  public MessageResponseDTO resetPassword(@RequestBody ResetPasswordRequestDTO request) {
    return accountManagementService.resetPassword(request);
  }

  /**
   * Maps every validation and credential failure in this controller to 400 with a {@code
   * {"message": ...}} body, matching the shape {@code GlobalExceptionHandler} produces elsewhere.
   *
   * <p>Note that a wrong password and an unknown username both land here as the same 400, so the
   * response does not reveal whether an account exists.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<MessageResponseDTO> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(new MessageResponseDTO(exception.getMessage()));
  }
}
