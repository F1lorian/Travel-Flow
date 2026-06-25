package com.sep.sep_backend.accountmanagement;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountManagementController {
  private final AccountManagementService accountManagementService;

  public AccountManagementController(AccountManagementService accountManagementService) {
    this.accountManagementService = accountManagementService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponseDTO> register(@RequestBody RegisterRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(accountManagementService.register(request));
  }

  @PostMapping("/login")
  public AuthResponseDTO login(@RequestBody LoginRequestDTO request) {
    return accountManagementService.login(request);
  }

  @PostMapping("/oauth/google")
  public AuthResponseDTO loginWithGoogle(@RequestBody GoogleLoginRequestDTO request) {
    return accountManagementService.loginWithGoogle(request);
  }

  @PostMapping("/verify-email")
  public MessageResponseDTO verifyEmail(@RequestBody VerifyEmailRequestDTO request) {
    return accountManagementService.verifyEmail(request);
  }

  @PostMapping("/forgot-password")
  public MessageResponseDTO forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
    return accountManagementService.forgotPassword(request);
  }

  @PostMapping("/send-reset-code")
  public MessageResponseDTO sendResetCode(@RequestBody SendResetCodeRequestDTO request) {
    return accountManagementService.sendResetCode(request);
  }

  @PostMapping("/reset-password-with-code")
  public MessageResponseDTO resetPasswordWithCode(
      @RequestBody ResetPasswordWithCodeRequestDTO request) {
    return accountManagementService.resetPasswordWithCode(request);
  }

  @PostMapping("/reset-password")
  public MessageResponseDTO resetPassword(@RequestBody ResetPasswordRequestDTO request) {
    return accountManagementService.resetPassword(request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<MessageResponseDTO> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(new MessageResponseDTO(exception.getMessage()));
  }
}
