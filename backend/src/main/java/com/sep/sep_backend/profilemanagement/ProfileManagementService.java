package com.sep.sep_backend.profilemanagement;

import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.accountmanagement.GoogleTokenVerifier;
import com.sep.sep_backend.accountmanagement.MessageResponseDTO;
import com.sep.sep_backend.repos.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Handles the logged-in user's own profile: viewing privacy details and changing the password. */
@Service
public class ProfileManagementService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileManagementService.class);
  private static final int CODE_MIN = 100000;
  private static final int CODE_MAX = 900000;
  private static final int VERIFICATION_MINUTES = 15;

  private final UserRepository userRepository;
  private final JavaMailSender mailSender;
  private final PasswordEncoder passwordEncoder;
  private final GoogleTokenVerifier googleTokenVerifier;
  private final String mailUsername;
  private final SecureRandom secureRandom = new SecureRandom();

  public ProfileManagementService(
      UserRepository userRepository,
      JavaMailSender mailSender,
      PasswordEncoder passwordEncoder,
      GoogleTokenVerifier googleTokenVerifier,
      @Value("${spring.mail.username:}") String mailUsername) {
    this.userRepository = userRepository;
    this.mailSender = mailSender;
    this.passwordEncoder = passwordEncoder;
    this.googleTokenVerifier = googleTokenVerifier;
    this.mailUsername = mailUsername;
  }

  public ProfileDetailsDTO getProfile(String username) {
    User user = findByUsername(username);
    return new ProfileDetailsDTO(
        user.getName(),
        user.getFirstName(),
        user.getSurname(),
        user.getEmail(),
        user.getProvider(),
        user.isEmailVerified());
  }

  /** Generates and e-mails a one-time code the user must enter to authorise a password change. */
  public MessageResponseDTO sendChangeCode(String username) {
    User user = findByUsername(username);
    user.setVerificationCode(createCode());
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
    userRepository.save(user);
    return new MessageResponseDTO(sendChangeCodeEmail(user));
  }

  public MessageResponseDTO changePassword(String username, ChangePasswordRequestDTO request) {
    User user = findByUsername(username);
    String newPassword = requireText(request.newPassword(), "New password is required");
    String repeatNewPassword =
        requireText(request.repeatNewPassword(), "Repeat new password is required");
    validatePassword(newPassword);
    if (!newPassword.equals(repeatNewPassword)) {
      throw new IllegalArgumentException("Passwords do not match");
    }

    verifyEmailOwnership(user, request.code(), request.googleIdToken());

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setVerificationCode(null);
    user.setVerificationCodeExpiresAt(null);
    userRepository.save(user);
    return new MessageResponseDTO("Password changed successfully");
  }

  /**
   * Permanently deletes the logged-in user's account after confirming ownership with an e-mail code
   * or a matching Google login. Friendships are symmetric, so the account is first unlinked from
   * every friend to clear the reverse {@code user_friends} rows before the user row is removed.
   */
  public MessageResponseDTO deleteAccount(String username, DeleteAccountRequestDTO request) {
    User user = findByUsername(username);
    verifyEmailOwnership(user, request.code(), request.googleIdToken());

    for (User friend : user.getFriends()) {
      friend.getFriends().removeIf(existing -> existing.getId() == user.getId());
      userRepository.save(friend);
    }
    user.getFriends().clear();

    userRepository.delete(user);
    return new MessageResponseDTO("Account deleted successfully");
  }

  /**
   * Confirms the request really comes from the account owner via an e-mail code or Google login.
   */
  private void verifyEmailOwnership(User user, String code, String googleIdToken) {
    if (StringUtils.hasText(googleIdToken)) {
      GoogleTokenVerifier.GoogleUserInfo googleUser = googleTokenVerifier.verify(googleIdToken);
      if (!googleUser.emailVerified() || !user.getEmail().equalsIgnoreCase(googleUser.email())) {
        throw new IllegalArgumentException("Google account does not match this profile");
      }
      return;
    }

    if (StringUtils.hasText(code)) {
      if (user.getVerificationCodeExpiresAt() == null
          || user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
        throw new IllegalArgumentException("Verification code expired");
      }
      if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
        throw new IllegalArgumentException("Verification code is invalid");
      }
      return;
    }

    throw new IllegalArgumentException(
        "Verify your email with a code or with Google before continuing");
  }

  private User findByUsername(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(
            requireText(username, "Not authenticated"), requireText(username, "Not authenticated"))
        .orElseThrow(() -> new IllegalArgumentException("Account not found"));
  }

  private String sendChangeCodeEmail(User user) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(user.getEmail());
    message.setSubject("SEP password change code");
    message.setText("Your SEP password change code is: " + user.getVerificationCode());
    try {
      mailSender.send(message);
      return "Verification code sent to your email";
    } catch (MailException exception) {
      LOGGER.warn("Password change code could not be sent to {}", user.getEmail(), exception);
      return "Email could not be sent. Your code is: " + user.getVerificationCode();
    }
  }

  private String createCode() {
    return String.valueOf(CODE_MIN + secureRandom.nextInt(CODE_MAX));
  }

  private void validatePassword(String password) {
    if (!password.matches("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{9,}$")) {
      throw new IllegalArgumentException(
          "Password must be at least 9 characters and include a capital letter, a number, and a symbol");
    }
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }
}
