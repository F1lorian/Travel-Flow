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

/**
 * Owns the whole account lifecycle: registration, email verification, login, and password recovery.
 *
 * <p>A new account starts unverified. Registration stores it, emails a six-digit code valid for 15
 * minutes, and returns <em>no</em> token; {@link #login} refuses until {@link #verifyEmail} has
 * run. Google sign-in ({@link #loginWithGoogle}) bypasses that entirely — Google has already proven
 * the address — and creates the account on first use.
 *
 * <p>There are two independent password-reset paths, and they are easy to confuse. The
 * question-based one ({@link #forgotPassword} then {@link #resetPassword}) authorises with the
 * security answer and re-checks it in both steps. The code-based one ({@link #sendResetCode} then
 * {@link #resetPasswordWithCode}) authorises with an emailed code, reusing the same {@code
 * verificationCode} field as registration.
 *
 * <p>Mail failures never fail the request. If the SMTP send throws, the code is returned inside the
 * response message instead so a developer without working mail can still finish the flow — see
 * {@link #sendVerificationEmail}. That is a convenience for this project's setup and would leak the
 * code to an unauthenticated caller in production.
 *
 * <p>Every rejection is an {@link IllegalArgumentException}, which the controller turns into a 400.
 */
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
  private final GoogleTokenVerifier googleTokenVerifier;
  private final String mailUsername;
  private final SecureRandom secureRandom = new SecureRandom();

  public AccountManagementService(
      UserRepository userRepository,
      JavaMailSender mailSender,
      JwtService jwtService,
      PasswordEncoder passwordEncoder,
      GoogleTokenVerifier googleTokenVerifier,
      @Value("${spring.mail.username:}") String mailUsername) {
    this.userRepository = userRepository;
    this.mailSender = mailSender;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
    this.googleTokenVerifier = googleTokenVerifier;
    this.mailUsername = mailUsername;
  }

  /**
   * Registers a new unverified account and sends its verification code.
   *
   * <p>The email is lower-cased before storage and both the password and the security answer are
   * BCrypt-hashed. The returned response carries no token by design — the account cannot log in
   * until the code is confirmed.
   *
   * @param request the sign-up data; all fields are required
   * @return a message describing what to do next, plus the created username
   * @throws IllegalArgumentException if any field is blank, if the username, email or password
   *     fails validation, if the two passwords differ, or if the username or email is already taken
   */
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

  /**
   * Authenticates with a password and issues a JWT.
   *
   * @param request username <em>or</em> email, plus the password
   * @return the signed token and the resolved username
   * @throws IllegalArgumentException if either field is blank, if no such account exists, if the
   *     password is wrong, or if the email has not been verified yet
   */
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

  /**
   * Signs in with a Google ID token, creating a local account on first use.
   *
   * <p>Accounts are matched on the verified Google email, so a user who registered with a password
   * and later uses Google lands on the same account rather than a duplicate.
   *
   * @param request the ID token issued to the frontend by Google Sign-In
   * @return the signed token and the local username
   * @throws IllegalArgumentException if the token is blank or fails verification, or if Google
   *     reports an account whose email is missing or unverified
   */
  public AuthResponseDTO loginWithGoogle(GoogleLoginRequestDTO request) {
    String idToken = requireText(request.idToken(), "Google credential is required");
    GoogleTokenVerifier.GoogleUserInfo googleUser = googleTokenVerifier.verify(idToken);

    String email = requireText(googleUser.email(), "Google account has no email").toLowerCase();
    if (!googleUser.emailVerified()) {
      throw new IllegalArgumentException("Google account email is not verified");
    }

    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseGet(() -> createGoogleUser(email, googleUser));

    return new AuthResponseDTO("Login successful", user.getName(), jwtService.createToken(user));
  }

  /**
   * Creates a local account for a Google identity, marked verified and flagged {@code provider =
   * GOOGLE}.
   */
  private User createGoogleUser(String email, GoogleTokenVerifier.GoogleUserInfo googleUser) {
    User user = new User();
    user.setEmail(email);
    user.setName(generateUniqueUsername(email));
    // OAuth accounts never sign in with a password; store an unusable random hash so the
    // not-null constraint is satisfied without exposing a guessable secret.
    user.setPasswordHash(passwordEncoder.encode(randomSecret()));
    user.setSecurityAnswerHash(passwordEncoder.encode(randomSecret()));
    user.setFirstName(googleUser.firstName());
    user.setSurname(googleUser.surname());
    user.setProvider("GOOGLE");
    user.setEmailVerified(true);
    return userRepository.save(user);
  }

  /**
   * Derives a free username from the local part of a Google email.
   *
   * <p>Strips characters the username rules forbid, then pads the result so it still satisfies
   * "starts with a letter, at least three characters" even for an address like {@code 1@x.de}. On a
   * collision it appends a random four-digit suffix and retries, so two people with the same local
   * part on different domains both get an account.
   */
  private String generateUniqueUsername(String email) {
    String base = email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9_]", "");
    if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
      base = "user" + base;
    }
    if (base.length() < 3) {
      base = base + "user";
    }
    String candidate = base;
    while (userRepository.existsByNameIgnoreCase(candidate)) {
      candidate = base + (1000 + secureRandom.nextInt(9000));
    }
    return candidate;
  }

  /** 24 random bytes, base64url-encoded — used as an unguessable placeholder secret. */
  private String randomSecret() {
    byte[] bytes = new byte[24];
    secureRandom.nextBytes(bytes);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Confirms a registration code and marks the account usable.
   *
   * <p>The code is cleared on success, so it cannot be replayed.
   *
   * @param request the account's email and the code from the verification mail
   * @return a confirmation message
   * @throws IllegalArgumentException if the email or code is blank, if no account has that email,
   *     or if the code has expired or does not match
   */
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

  /**
   * Verifies the security answer so the client may show the new-password form.
   *
   * <p>Step one of the question-based reset. It only checks — nothing is changed and no token is
   * issued, which is why {@link #resetPassword} has to re-check the same answer.
   *
   * @param request username or email, plus the security answer
   * @return a message telling the client to collect a new password
   * @throws IllegalArgumentException if the account does not exist, the answer is blank, or the
   *     answer is wrong
   */
  public MessageResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
    User user = findByNameOrEmail(request.username());
    String favoriteColor = requireText(request.favoriteColor(), "Favorite color is required");

    if (!passwordEncoder.matches(favoriteColor, user.getSecurityAnswerHash())) {
      throw new IllegalArgumentException("Security answer is wrong");
    }

    return new MessageResponseDTO("Security answer verified. Enter a new password");
  }

  /**
   * Issues a fresh reset code, valid 15 minutes, and emails it.
   *
   * <p>Step one of the code-based reset. Overwrites any code already outstanding for the account.
   *
   * @param request the username or email of the account to recover
   * @return a message confirming the send, or one containing the code if the mail could not be sent
   * @throws IllegalArgumentException if the field is blank or no such account exists
   */
  public MessageResponseDTO sendResetCode(SendResetCodeRequestDTO request) {
    User user = findByNameOrEmail(request.username());
    user.setVerificationCode(createCode());
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
    userRepository.save(user);
    String message = sendResetCodeEmail(user);
    return new MessageResponseDTO(message);
  }

  /**
   * Sets a new password against a valid emailed code.
   *
   * <p>Step two of the code-based reset. The code is cleared on success so it cannot be reused.
   *
   * @param request the account, the emailed code, and the new password twice
   * @return a confirmation message
   * @throws IllegalArgumentException if a field is blank, the account does not exist, the new
   *     password fails the strength rules or the two copies differ, or the code has expired or does
   *     not match
   */
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

  /**
   * Sets a new password against the security answer.
   *
   * <p>Step two of the question-based reset. The answer is checked again here because {@link
   * #forgotPassword} hands out nothing that proves it was ever passed.
   *
   * @param request the account, the security answer, and the new password twice
   * @return a confirmation message
   * @throws IllegalArgumentException if a field is blank, the account does not exist, the new
   *     password fails the strength rules or the two copies differ, or the answer is wrong
   */
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

  /**
   * Looks up an account by username or email, case-insensitively — the single value is tried
   * against both columns, so users need not remember which one they signed up with.
   */
  private User findByNameOrEmail(String usernameOrEmail) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(
            requireText(usernameOrEmail, "Username or email is required"),
            requireText(usernameOrEmail, "Username or email is required"))
        .orElseThrow(
            () -> new IllegalArgumentException("No account found for this username or email"));
  }

  /** Looks up an account by email only, case-insensitively. */
  private User findByEmail(String email) {
    return userRepository
        .findByEmailIgnoreCase(requireText(email, "Email is required"))
        .orElseThrow(() -> new IllegalArgumentException("No account found for this email"));
  }

  /**
   * Rejects a blank value with the given message and returns it trimmed. The trim is why callers
   * must use the returned value rather than the original.
   */
  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  /**
   * Enforces the username rules: at least three characters, starting with a letter, and otherwise
   * only letters, digits and underscores.
   */
  private void validateUsername(String username) {
    if (!username.matches("^[A-Za-z][A-Za-z0-9_]{2,}$")) {
      throw new IllegalArgumentException(
          "Username must start with a letter and be at least 3 characters. Use only letters, numbers, and underscores");
    }
  }

  /**
   * Enforces the password rules: at least nine characters including a capital letter, a digit and a
   * symbol. Applied on registration and on both reset paths, but never on login, so accounts
   * created before a rule change keep working.
   */
  private void validatePassword(String password) {
    if (!password.matches("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{9,}$")) {
      throw new IllegalArgumentException(
          "Password must be at least 9 characters and include a capital letter, a number, and a symbol");
    }
  }

  /**
   * Checks the email shape loosely — something, an {@code @}, then a dotted domain, with no spaces.
   * Deliberately permissive; deliverability is proven by the verification code, not by this regex.
   */
  private void validateEmail(String email) {
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new IllegalArgumentException("Email is invalid");
    }
  }

  /** Generates a six-digit verification code from a {@link SecureRandom}. */
  private String createCode() {
    return String.valueOf(CODE_MIN + secureRandom.nextInt(CODE_MAX));
  }

  /**
   * Emails the reset code and returns the message to show the user.
   *
   * <p>See {@link #sendVerificationEmail} — a send failure is logged and the code is handed back in
   * the response instead of aborting the reset.
   */
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

  /**
   * Emails the registration code and returns the message to show the user.
   *
   * <p>A {@link MailException} is caught rather than propagated: the account has already been
   * saved, so failing the request here would leave an unverifiable account behind and no way to
   * reach it. The fallback puts the code straight into the response, which keeps local development
   * usable without SMTP but would disclose it to an unauthenticated caller in production.
   */
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
