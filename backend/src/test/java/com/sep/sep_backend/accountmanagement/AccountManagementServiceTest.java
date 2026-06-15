package com.sep.sep_backend.accountmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private JavaMailSender mailSender;
  @Mock private JwtService jwtService;

  private AccountManagementService service;

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  @BeforeEach
  void setUp() {
    service =
        new AccountManagementService(userRepository, mailSender, jwtService, "noreply@example.com");
  }

  private User verifiedUser(String username, String email, String password, String color) {
    User user = new User(username, email, ENCODER.encode(password), ENCODER.encode(color));
    user.setEmailVerified(true);
    return user;
  }

  // --- register ---

  @Test
  void registerErstelltBenutzerkontoMitVerifikationscode() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max",
            "Mustermann",
            "maxmuster",
            "max@example.com",
            "Password1!",
            "Password1!",
            "blue");
    when(userRepository.existsByNameIgnoreCase("maxmuster")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("max@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    AuthResponseDTO result = service.register(request);

    assertThat(result.username()).isEqualTo("maxmuster");
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getVerificationCode()).isNotNull();
    assertThat(captor.getValue().isEmailVerified()).isFalse();
  }

  @Test
  void registerWirftExceptionBeiLeeremBenutzernamen() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max", "Mustermann", "", "max@example.com", "Password1!", "Password1!", "blue");

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Username is required");

    verify(userRepository, never()).save(any());
  }

  @Test
  void registerWirftExceptionBeiUngueltigemBenutzernamen() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max", "Mustermann", "1invalid", "max@example.com", "Password1!", "Password1!", "blue");

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Username must start with a letter");

    verify(userRepository, never()).save(any());
  }

  @Test
  void registerWirftExceptionBeiUngueltigerEmail() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max", "Mustermann", "maxmuster", "not-an-email", "Password1!", "Password1!", "blue");

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email is invalid");

    verify(userRepository, never()).save(any());
  }

  @Test
  void registerWirftExceptionBeiZuSchwachesPasswort() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max", "Mustermann", "maxmuster", "max@example.com", "password", "password", "blue");

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password must be at least 9 characters");

    verify(userRepository, never()).save(any());
  }

  @Test
  void registerWirftExceptionWennPasswoerterNichtUebereinstimmen() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max",
            "Mustermann",
            "maxmuster",
            "max@example.com",
            "Password1!",
            "Different1!",
            "blue");

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Passwords do not match");

    verify(userRepository, never()).save(any());
  }

  @Test
  void registerWirftExceptionWennBenutzernameSchonVergeben() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max",
            "Mustermann",
            "maxmuster",
            "max@example.com",
            "Password1!",
            "Password1!",
            "blue");
    when(userRepository.existsByNameIgnoreCase("maxmuster")).thenReturn(true);

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Username is already used");

    verify(userRepository, never()).save(any());
  }

  @Test
  void registerWirftExceptionWennEmailSchonVergeben() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max",
            "Mustermann",
            "maxmuster",
            "max@example.com",
            "Password1!",
            "Password1!",
            "blue");
    when(userRepository.existsByNameIgnoreCase("maxmuster")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("max@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email is already used");

    verify(userRepository, never()).save(any());
  }

  // --- login ---

  @Test
  void loginGibtTokenZurueckBeiErfolg() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(jwtService.createToken(user)).thenReturn("jwt-token");

    AuthResponseDTO result = service.login(new LoginRequestDTO("maxmuster", "Password1!"));

    assertThat(result.token()).isEqualTo("jwt-token");
    assertThat(result.username()).isEqualTo("maxmuster");
  }

  @Test
  void loginWirftExceptionBeiFalschenZugangsdaten() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.login(new LoginRequestDTO("maxmuster", "WrongPass1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Wrong username or password");
  }

  @Test
  void loginWirftExceptionWennEmailNichtVerifiziert() {
    User user =
        new User(
            "maxmuster", "max@example.com", ENCODER.encode("Password1!"), ENCODER.encode("blue"));
    user.setEmailVerified(false);
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.login(new LoginRequestDTO("maxmuster", "Password1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Please verify your email before logging in");
  }

  @Test
  void loginWirftExceptionWennBenutzerNichtGefunden() {
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("unknown", "unknown"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginRequestDTO("unknown", "Password1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No account found for this username or email");
  }

  // --- verifyEmail ---

  @Test
  void verifyEmailVerifiziertEmailErfolgreich() {
    User user =
        new User(
            "maxmuster", "max@example.com", ENCODER.encode("Password1!"), ENCODER.encode("blue"));
    user.setEmailVerified(false);
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
    when(userRepository.findByEmailIgnoreCase("max@example.com")).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    MessageResponseDTO result =
        service.verifyEmail(new VerifyEmailRequestDTO("max@example.com", "123456"));

    assertThat(result.message()).contains("verified");
    assertThat(user.isEmailVerified()).isTrue();
    assertThat(user.getVerificationCode()).isNull();
  }

  @Test
  void verifyEmailWirftExceptionBeiAbgelaufenemCode() {
    User user =
        new User(
            "maxmuster", "max@example.com", ENCODER.encode("Password1!"), ENCODER.encode("blue"));
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(1));
    when(userRepository.findByEmailIgnoreCase("max@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.verifyEmail(new VerifyEmailRequestDTO("max@example.com", "123456")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Verification code expired");
  }

  @Test
  void verifyEmailWirftExceptionBeiFalschenCode() {
    User user =
        new User(
            "maxmuster", "max@example.com", ENCODER.encode("Password1!"), ENCODER.encode("blue"));
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
    when(userRepository.findByEmailIgnoreCase("max@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.verifyEmail(new VerifyEmailRequestDTO("max@example.com", "999999")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Verification code is invalid");
  }

  // --- forgotPassword ---

  @Test
  void forgotPasswordBestaetigtRichtigeSicherheitsantwort() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    MessageResponseDTO result =
        service.forgotPassword(new ForgotPasswordRequestDTO("maxmuster", "blue"));

    assertThat(result.message()).contains("Security answer verified");
  }

  @Test
  void forgotPasswordWirftExceptionBeiFalscherSicherheitsantwort() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.forgotPassword(new ForgotPasswordRequestDTO("maxmuster", "red")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Security answer is wrong");
  }

  // --- sendResetCode ---

  @Test
  void sendResetCodeSpeichertCodeUndGibtNachrichtZurueck() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    MessageResponseDTO result = service.sendResetCode(new SendResetCodeRequestDTO("maxmuster"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getVerificationCode()).isNotNull();
    assertThat(captor.getValue().getVerificationCodeExpiresAt()).isAfter(LocalDateTime.now());
    assertThat(result.message()).isNotBlank();
  }

  @Test
  void sendResetCodeWirftExceptionFuerUnbekanntenBenutzer() {
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("unknown", "unknown"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.sendResetCode(new SendResetCodeRequestDTO("unknown")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No account found for this username or email");

    verify(userRepository, never()).save(any());
  }

  // --- resetPasswordWithCode ---

  @Test
  void resetPasswordWithCodeSetzNeuesPasswortErfolgreich() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    MessageResponseDTO result =
        service.resetPasswordWithCode(
            new ResetPasswordWithCodeRequestDTO("maxmuster", "123456", "NewPass1!", "NewPass1!"));

    assertThat(result.message()).contains("Password changed");
    assertThat(ENCODER.matches("NewPass1!", user.getPasswordHash())).isTrue();
    assertThat(user.getVerificationCode()).isNull();
  }

  @Test
  void resetPasswordWithCodeWirftExceptionBeiAbgelaufenemCode() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(1));
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                service.resetPasswordWithCode(
                    new ResetPasswordWithCodeRequestDTO(
                        "maxmuster", "123456", "NewPass1!", "NewPass1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Reset code has expired");
  }

  @Test
  void resetPasswordWithCodeWirftExceptionBeiFalschenCode() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                service.resetPasswordWithCode(
                    new ResetPasswordWithCodeRequestDTO(
                        "maxmuster", "999999", "NewPass1!", "NewPass1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Reset code is invalid");
  }

  @Test
  void resetPasswordWithCodeWirftExceptionWennPasswoerterNichtUebereinstimmen() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                service.resetPasswordWithCode(
                    new ResetPasswordWithCodeRequestDTO(
                        "maxmuster", "123456", "NewPass1!", "Different1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Passwords do not match");
  }

  // --- resetPassword (via security answer) ---

  @Test
  void resetPasswordSetzNeuesPasswortErfolgreich() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    MessageResponseDTO result =
        service.resetPassword(
            new ResetPasswordRequestDTO("maxmuster", "blue", "NewPass1!", "NewPass1!"));

    assertThat(result.message()).contains("Password changed");
    assertThat(ENCODER.matches("NewPass1!", user.getPasswordHash())).isTrue();
  }

  @Test
  void resetPasswordWirftExceptionBeiFalscherSicherheitsantwort() {
    User user = verifiedUser("maxmuster", "max@example.com", "Password1!", "blue");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                service.resetPassword(
                    new ResetPasswordRequestDTO("maxmuster", "red", "NewPass1!", "NewPass1!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Security answer is wrong");

    verify(userRepository, never()).save(any());
  }
}
