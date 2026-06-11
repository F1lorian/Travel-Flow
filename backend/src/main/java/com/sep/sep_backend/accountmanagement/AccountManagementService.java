package com.sep.sep_backend.accountmanagement;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountManagementService {
  private final UserAccountRepository userAccountRepository;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public AccountManagementService(UserAccountRepository userAccountRepository) {
    this.userAccountRepository = userAccountRepository;
  }

  public AuthResponseDTO register(RegisterRequestDTO request) {
    String username = requireText(request.username(), "Username is required");
    String password = requireText(request.password(), "Password is required");
    String repeatPassword = requireText(request.repeatPassword(), "Repeat password is required");
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");

    validateUsername(username);
    validatePassword(password);
    if (!password.equals(repeatPassword)) {
      throw new IllegalArgumentException("Passwords do not match");
    }
    requireText(request.firstName(), "First name is required");
    requireText(request.surname(), "Surname is required");

    if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
      throw new IllegalArgumentException("Username is already used");
    }

    UserAccount user =
        new UserAccount(
            username, passwordEncoder.encode(password), passwordEncoder.encode(favoriteColor));
    userAccountRepository.save(user);

    return toResponse("Account created successfully", user);
  }

  public AuthResponseDTO login(LoginRequestDTO request) {
    String username = requireText(request.username(), "Username is required");
    String password = requireText(request.password(), "Password is required");

    UserAccount user = findByUsername(username);

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new IllegalArgumentException("Wrong username or password");
    }

    return toResponse("Login successful", user);
  }

  public MessageResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
    UserAccount user = findByUsername(request.username());
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");

    if (!passwordEncoder.matches(favoriteColor, user.getSecurityAnswerHash())) {
      throw new IllegalArgumentException("Security answer is wrong");
    }

    return new MessageResponseDTO("Security answer verified. Enter a new password");
  }

  public MessageResponseDTO resetPassword(ResetPasswordRequestDTO request) {
    UserAccount user = findByUsername(request.username());
    String newPassword = requireText(request.newPassword(), "New password is required");
    String repeatNewPassword =
        requireText(request.repeatNewPassword(), "Repeat new password is required");
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");
    validatePassword(newPassword);

    if (!newPassword.equals(repeatNewPassword)) {
      throw new IllegalArgumentException("Passwords do not match");
    }

    if (!passwordEncoder.matches(favoriteColor, user.getSecurityAnswerHash())) {
      throw new IllegalArgumentException("Security answer is wrong");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userAccountRepository.save(user);
    return new MessageResponseDTO("Password changed successfully");
  }

  private UserAccount findByUsername(String username) {
    return userAccountRepository
        .findByUsernameIgnoreCase(requireText(username, "Username is required"))
        .orElseThrow(() -> new IllegalArgumentException("No account found for this username"));
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private void validateUsername(String username) {
    if (!username.matches("^[A-Za-z][A-Za-z0-9_]{2,}$")) {
      throw new IllegalArgumentException(
          "Username must start with a letter and be at least 3 characters. Use only letters, numbers, and underscores");
    }
  }

  private void validatePassword(String password) {
    if (!password.matches("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{9,}$")) {
      throw new IllegalArgumentException(
          "Password must be at least 9 characters and include a capital letter, a number, and a symbol");
    }
  }

  private AuthResponseDTO toResponse(String message, UserAccount user) {
    return new AuthResponseDTO(message, user.getUsername());
  }
}
