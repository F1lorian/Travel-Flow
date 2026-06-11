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
    return ResponseEntity.status(HttpStatus.CREATED).body(accountManagementService.register(request));
  }

  @PostMapping("/login")
  public AuthResponseDTO login(@RequestBody LoginRequestDTO request) {
    return accountManagementService.login(request);
  }

  @PostMapping("/forgot-password")
  public MessageResponseDTO forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
    return accountManagementService.forgotPassword(request);
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
