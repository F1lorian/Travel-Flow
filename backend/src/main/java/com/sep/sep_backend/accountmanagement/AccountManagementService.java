package com.sep.sep_backend.accountmanagement;

import com.sep.sep_backend.entities.User;
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

@Service
public class AccountManagementService {
  private static final Logger LOGGER = LoggerFactory.getLogger(AccountManagementService.class);
  private static final int CODE_MIN = 100000;
  private static final int CODE_MAX = 900000;
  private static final int VERIFICATION_MINUTES = 15;
  private static final String SECURITY_QUESTION = "What is your favorite color?";

  private final UserRepository userRepository;
  private final JavaMailSender mailSender;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final String mailUsername;
  private final SecureRandom secureRandom = new SecureRandom();

  public AccountManagementService(
      UserRepository userRepository,
      JavaMailSender mailSender,
      JwtService jwtService,
      PasswordEncoder passwordEncoder,
      @Value("${spring.mail.username:}") String mailUsername) {
    this.userRepository = userRepository;
    this.mailSender = mailSender;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
    this.mailUsername = mailUsername;
  }

  public AuthResponseDTO register(RegisterRequestDTO request) {
    String name = requireText(request.username(), "Username is required");
    String email = requireText(request.email(), "Email is required").toLowerCase();
    String password = requireText(request.password(), "Password is required");
    String repeatPassword = requireText(request.repeatPassword(), "Repeat password is required");
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");
    String firstName = requireText(request.firstName(), "First name is required");
    String surname = requireText(request.surname(), "Surname is required");

    validateUsername(name);
    validateEmail(email);
    validatePassword(password);
    if (!password.equals(repeatPassword)) {
      throw new IllegalArgumentException("Passwords do not match");
    }

    if (userRepository.existsByNameIgnoreCase(name)) {
      throw new IllegalArgumentException("Username is already used");
    }
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new IllegalArgumentException("Email is already used");
    }

    User user =
        new User(
            name, email, passwordEncoder.encode(password), passwordEncoder.encode(favoriteColor));
    user.setFirstName(firstName);
    user.setSurname(surname);
    user.setSecurityQuestion(SECURITY_QUESTION);
    user.setEmailVerified(false);
    user.setVerificationCode(createCode());
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
    userRepository.save(user);

    String message = sendVerificationEmail(user);
    return new AuthResponseDTO(message, user.getName(), null);
  }

  public AuthResponseDTO login(LoginRequestDTO request) {
    String usernameOrEmail = requireText(request.username(), "Username or email is required");
    String password = requireText(request.password(), "Password is required");

    User user = findByNameOrEmail(usernameOrEmail);

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new IllegalArgumentException("Wrong username or password");
    }
    if (!user.isEmailVerified()) {
      throw new IllegalArgumentException("Please verify your email before logging in");
    }

    return new AuthResponseDTO("Login successful", user.getName(), jwtService.createToken(user));
  }

  public MessageResponseDTO verifyEmail(VerifyEmailRequestDTO request) {
    User user = findByEmail(requireText(request.email(), "Email is required"));
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
    userRepository.save(user);
    return new MessageResponseDTO("Email verified successfully. You can log in now");
  }

  public MessageResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
    User user = findByNameOrEmail(request.username());
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");

    if (!passwordEncoder.matches(favoriteColor, user.getSecurityAnswerHash())) {
      throw new IllegalArgumentException("Security answer is wrong");
    }

    return new MessageResponseDTO("Security answer verified. Enter a new password");
  }

  public MessageResponseDTO sendResetCode(SendResetCodeRequestDTO request) {
    User user = findByNameOrEmail(request.username());
    user.setVerificationCode(createCode());
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
    userRepository.save(user);
    String message = sendResetCodeEmail(user);
    return new MessageResponseDTO(message);
  }

  public MessageResponseDTO resetPasswordWithCode(ResetPasswordWithCodeRequestDTO request) {
    User user = findByNameOrEmail(request.username());
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
    userRepository.save(user);
    return new MessageResponseDTO("Password changed successfully");
  }

  public MessageResponseDTO resetPassword(ResetPasswordRequestDTO request) {
    User user = findByNameOrEmail(request.username());
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
    userRepository.save(user);
    return new MessageResponseDTO("Password changed successfully");
  }

  private User findByNameOrEmail(String usernameOrEmail) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(
            requireText(usernameOrEmail, "Username or email is required"),
            requireText(usernameOrEmail, "Username or email is required"))
        .orElseThrow(
            () -> new IllegalArgumentException("No account found for this username or email"));
  }

  private User findByEmail(String email) {
    return userRepository
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

  private String sendResetCodeEmail(User user) {
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

  private String sendVerificationEmail(User user) {
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
