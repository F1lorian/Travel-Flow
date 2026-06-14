package com.sep.sep_backend.accountmanagement;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountManagementService {
  private static final Logger LOGGER = LoggerFactory.getLogger(AccountManagementService.class);
  private static final int CODE_MIN = 100000;
  private static final int CODE_MAX = 900000;
  private static final int VERIFICATION_MINUTES = 15;

  private final UserAccountRepository userAccountRepository;
  private final JavaMailSender mailSender;
  private final JwtService jwtService;
  private final String mailUsername;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final SecureRandom secureRandom = new SecureRandom();

  public AccountManagementService(
      UserAccountRepository userAccountRepository,
      JavaMailSender mailSender,
      JwtService jwtService,
      @Value("${spring.mail.username:}") String mailUsername) {
    this.userAccountRepository = userAccountRepository;
    this.mailSender = mailSender;
    this.jwtService = jwtService;
    this.mailUsername = mailUsername;
  }

  public AuthResponseDTO register(RegisterRequestDTO request) {
    String username = requireText(request.username(), "Username is required");
    String email = requireText(request.email(), "Email is required").toLowerCase();
    String password = requireText(request.password(), "Password is required");
    String repeatPassword = requireText(request.repeatPassword(), "Repeat password is required");
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");

    validateUsername(username);
    validateEmail(email);
    validatePassword(password);
    if (!password.equals(repeatPassword)) {
      throw new IllegalArgumentException("Passwords do not match");
    }
    requireText(request.firstName(), "First name is required");
    requireText(request.surname(), "Surname is required");

    if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
      throw new IllegalArgumentException("Username is already used");
    }
    if (userAccountRepository.existsByEmailIgnoreCase(email)) {
      throw new IllegalArgumentException("Email is already used");
    }

    UserAccount user =
        new UserAccount(
            username,
            email,
            passwordEncoder.encode(password),
            passwordEncoder.encode(favoriteColor));
    user.setEmailVerified(false);
    user.setVerificationCode(createCode());
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
    userAccountRepository.save(user);

    String message = sendVerificationEmail(user);
    return new AuthResponseDTO(message, user.getUsername(), null);
  }

  public AuthResponseDTO login(LoginRequestDTO request) {
    String usernameOrEmail = requireText(request.username(), "Username or email is required");
    String password = requireText(request.password(), "Password is required");

    UserAccount user = findByUsernameOrEmail(usernameOrEmail);

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new IllegalArgumentException("Wrong username or password");
    }
    if (!user.isEmailVerified()) {
      throw new IllegalArgumentException("Please verify your email before logging in");
    }

    return new AuthResponseDTO(
        "Login successful", user.getUsername(), jwtService.createToken(user));
  }

  public MessageResponseDTO verifyEmail(VerifyEmailRequestDTO request) {
    UserAccount user = findByEmail(requireText(request.email(), "Email is required"));
    String code = requireText(request.code(), "Verification code is required");

    if (user.getVerificationCodeExpiresAt() == null
        || user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
      throw new IllegalArgumentException("Verification code expired");
    }
    if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
      throw new IllegalArgumentException("Verification code is invalid");
    }

    user.setEmailVerified(true);
    user.setVerificationCode(null);
    user.setVerificationCodeExpiresAt(null);
    userAccountRepository.save(user);
    return new MessageResponseDTO("Email verified successfully. You can log in now");
  }

  public MessageResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
    UserAccount user = findByUsernameOrEmail(request.username());
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");

    if (!passwordEncoder.matches(favoriteColor, user.getSecurityAnswerHash())) {
      throw new IllegalArgumentException("Security answer is wrong");
    }

    return new MessageResponseDTO("Security answer verified. Enter a new password");
  }

  public MessageResponseDTO sendResetCode(SendResetCodeRequestDTO request) {
    UserAccount user = findByUsernameOrEmail(request.username());
    user.setVerificationCode(createCode());
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
    userAccountRepository.save(user);
    String message = sendResetCodeEmail(user);
    return new MessageResponseDTO(message);
  }

  public MessageResponseDTO resetPasswordWithCode(ResetPasswordWithCodeRequestDTO request) {
    UserAccount user = findByUsernameOrEmail(request.username());
    String code = requireText(request.code(), "Reset code is required");
    String newPassword = requireText(request.newPassword(), "New password is required");
    String repeatNewPassword =
        requireText(request.repeatNewPassword(), "Repeat new password is required");
    validatePassword(newPassword);
    if (!newPassword.equals(repeatNewPassword)) {
      throw new IllegalArgumentException("Passwords do not match");
    }
    if (user.getVerificationCodeExpiresAt() == null
        || user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
      throw new IllegalArgumentException("Reset code has expired");
    }
    if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
      throw new IllegalArgumentException("Reset code is invalid");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setVerificationCode(null);
    user.setVerificationCodeExpiresAt(null);
    userAccountRepository.save(user);
    return new MessageResponseDTO("Password changed successfully");
  }

  public MessageResponseDTO resetPassword(ResetPasswordRequestDTO request) {
    UserAccount user = findByUsernameOrEmail(request.username());
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

  private UserAccount findByUsernameOrEmail(String usernameOrEmail) {
    return userAccountRepository
        .findByUsernameIgnoreCaseOrEmailIgnoreCase(
            requireText(usernameOrEmail, "Username or email is required"),
            requireText(usernameOrEmail, "Username or email is required"))
        .orElseThrow(
            () -> new IllegalArgumentException("No account found for this username or email"));
  }

  private UserAccount findByEmail(String email) {
    return userAccountRepository
        .findByEmailIgnoreCase(requireText(email, "Email is required"))
        .orElseThrow(() -> new IllegalArgumentException("No account found for this email"));
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

  private void validateEmail(String email) {
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new IllegalArgumentException("Email is invalid");
    }
  }

  private String createCode() {
    return String.valueOf(CODE_MIN + secureRandom.nextInt(CODE_MAX));
  }

  private String sendResetCodeEmail(UserAccount user) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(user.getEmail());
    message.setSubject("SEP password reset code");
    message.setText("Your SEP password reset code is: " + user.getVerificationCode());
    try {
      mailSender.send(message);
      return "Reset code sent to your email";
    } catch (MailException exception) {
      LOGGER.warn("Reset email could not be sent to {}", user.getEmail(), exception);
      return "Reset email could not be sent. Your code is: " + user.getVerificationCode();
    }
  }

  private String sendVerificationEmail(UserAccount user) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(user.getEmail());
    message.setSubject("SEP email verification code");
    message.setText("Your SEP verification code is: " + user.getVerificationCode());

    try {
      mailSender.send(message);
      return "Account created. Verification code sent to your email";
    } catch (MailException exception) {
      LOGGER.warn("Verification email could not be sent to {}", user.getEmail(), exception);
      return "Account created. Verification email could not be sent. Your code is: "
          + user.getVerificationCode();
    }
  }
}
