package com.sep.sep_backend.accountmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AccountManagementControllerTest {

  private final AccountManagementService service = mock(AccountManagementService.class);
  private final AccountManagementController controller = new AccountManagementController(service);

  @Test
  void registerGibtCreatedStatusZurueck() {
    RegisterRequestDTO request =
        new RegisterRequestDTO(
            "Max",
            "Mustermann",
            "maxmuster",
            "max@example.com",
            "Password1!",
            "Password1!",
            "blue");
    AuthResponseDTO authResponse = new AuthResponseDTO("Account created", "maxmuster", null);
    when(service.register(request)).thenReturn(authResponse);

    ResponseEntity<AuthResponseDTO> response = controller.register(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isSameAs(authResponse);
    verify(service).register(request);
  }

  @Test
  void loginGibtAuthResponseZurueck() {
    LoginRequestDTO request = new LoginRequestDTO("maxmuster", "Password1!");
    AuthResponseDTO authResponse =
        new AuthResponseDTO("Login successful", "maxmuster", "jwt-token");
    when(service.login(request)).thenReturn(authResponse);

    AuthResponseDTO result = controller.login(request);

    assertThat(result).isSameAs(authResponse);
    verify(service).login(request);
  }

  @Test
  void verifyEmailGibtMessageResponseZurueck() {
    VerifyEmailRequestDTO request = new VerifyEmailRequestDTO("max@example.com", "123456");
    MessageResponseDTO messageResponse = new MessageResponseDTO("Email verified successfully");
    when(service.verifyEmail(request)).thenReturn(messageResponse);

    MessageResponseDTO result = controller.verifyEmail(request);

    assertThat(result).isSameAs(messageResponse);
    verify(service).verifyEmail(request);
  }

  @Test
  void forgotPasswordGibtMessageResponseZurueck() {
    ForgotPasswordRequestDTO request = new ForgotPasswordRequestDTO("maxmuster", "blue");
    MessageResponseDTO messageResponse = new MessageResponseDTO("Security answer verified");
    when(service.forgotPassword(request)).thenReturn(messageResponse);

    MessageResponseDTO result = controller.forgotPassword(request);

    assertThat(result).isSameAs(messageResponse);
    verify(service).forgotPassword(request);
  }

  @Test
  void sendResetCodeGibtMessageResponseZurueck() {
    SendResetCodeRequestDTO request = new SendResetCodeRequestDTO("maxmuster");
    MessageResponseDTO messageResponse = new MessageResponseDTO("Reset code sent to your email");
    when(service.sendResetCode(request)).thenReturn(messageResponse);

    MessageResponseDTO result = controller.sendResetCode(request);

    assertThat(result).isSameAs(messageResponse);
    verify(service).sendResetCode(request);
  }

  @Test
  void resetPasswordWithCodeGibtMessageResponseZurueck() {
    ResetPasswordWithCodeRequestDTO request =
        new ResetPasswordWithCodeRequestDTO("maxmuster", "123456", "NewPass1!", "NewPass1!");
    MessageResponseDTO messageResponse = new MessageResponseDTO("Password changed successfully");
    when(service.resetPasswordWithCode(request)).thenReturn(messageResponse);

    MessageResponseDTO result = controller.resetPasswordWithCode(request);

    assertThat(result).isSameAs(messageResponse);
    verify(service).resetPasswordWithCode(request);
  }

  @Test
  void resetPasswordGibtMessageResponseZurueck() {
    ResetPasswordRequestDTO request =
        new ResetPasswordRequestDTO("maxmuster", "blue", "NewPass1!", "NewPass1!");
    MessageResponseDTO messageResponse = new MessageResponseDTO("Password changed successfully");
    when(service.resetPassword(request)).thenReturn(messageResponse);

    MessageResponseDTO result = controller.resetPassword(request);

    assertThat(result).isSameAs(messageResponse);
    verify(service).resetPassword(request);
  }

  @Test
  void exceptionHandlerGibtBadRequestZurueckFuerIllegalArgumentException() {
    IllegalArgumentException exception = new IllegalArgumentException("Username is required");

    ResponseEntity<MessageResponseDTO> response = controller.handleBadRequest(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("Username is required");
  }
}
