package com.sep.sep_backend.profilemanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.Enitys.User;
import com.sep.sep_backend.Enitys.UserTrip;
import com.sep.sep_backend.accountmanagement.GoogleTokenVerifier;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ProfileManagementServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserTripRepository userTripRepository;
  @Mock private AusgabeRepository ausgabeRepository;
  @Mock private TripRepository tripRepository;
  @Mock private EventRepository eventRepository;
  @Mock private UserEventRepository userEventRepository;
  @Mock private JavaMailSender mailSender;
  @Mock private GoogleTokenVerifier googleTokenVerifier;

  private ProfileManagementService service;

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
  private static final String VALID_NEW_PASSWORD = "NewPass1!word";

  @BeforeEach
  void setUp() {
    service =
        new ProfileManagementService(
            userRepository,
            userTripRepository,
            ausgabeRepository,
            tripRepository,
            eventRepository,
            userEventRepository,
            mailSender,
            ENCODER,
            googleTokenVerifier,
            "noreply@example.com");
  }

  private User user(String username, String email) {
    User user = new User(username, email, ENCODER.encode("OldPass1!word"), ENCODER.encode("blue"));
    user.setFirstName("Max");
    user.setSurname("Mustermann");
    user.setEmailVerified(true);
    return user;
  }

  private void mockLookup(User user) {
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase(user.getName(), user.getName()))
        .thenReturn(Optional.of(user));
  }

  @Test
  void getProfileReturnsPrivacyDetails() {
    User user = user("max", "max@example.com");
    mockLookup(user);

    ProfileDetailsDTO details = service.getProfile("max");

    assertThat(details.username()).isEqualTo("max");
    assertThat(details.firstName()).isEqualTo("Max");
    assertThat(details.surname()).isEqualTo("Mustermann");
    assertThat(details.email()).isEqualTo("max@example.com");
  }

  @Test
  void sendChangeCodeStoresCodeAndSaves() {
    User user = user("max", "max@example.com");
    mockLookup(user);

    service.sendChangeCode("max");

    assertThat(user.getVerificationCode()).isNotBlank();
    assertThat(user.getVerificationCodeExpiresAt()).isAfter(LocalDateTime.now());
    verify(userRepository).save(user);
  }

  @Test
  void changePasswordWithValidCodeUpdatesPassword() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
    mockLookup(user);

    service.changePassword(
        "max",
        new ChangePasswordRequestDTO(VALID_NEW_PASSWORD, VALID_NEW_PASSWORD, "123456", null));

    assertThat(ENCODER.matches(VALID_NEW_PASSWORD, user.getPasswordHash())).isTrue();
    assertThat(user.getVerificationCode()).isNull();
    verify(userRepository).save(user);
  }

  @Test
  void changePasswordWithExpiredCodeFails() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(1));
    mockLookup(user);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "max",
                    new ChangePasswordRequestDTO(
                        VALID_NEW_PASSWORD, VALID_NEW_PASSWORD, "123456", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expired");
    verify(userRepository, never()).save(any());
  }

  @Test
  void changePasswordWithWrongCodeFails() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
    mockLookup(user);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "max",
                    new ChangePasswordRequestDTO(
                        VALID_NEW_PASSWORD, VALID_NEW_PASSWORD, "000000", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid");
    verify(userRepository, never()).save(any());
  }

  @Test
  void changePasswordWithoutVerificationFails() {
    User user = user("max", "max@example.com");
    mockLookup(user);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "max",
                    new ChangePasswordRequestDTO(
                        VALID_NEW_PASSWORD, VALID_NEW_PASSWORD, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Verify your email");
    verify(userRepository, never()).save(any());
  }

  @Test
  void changePasswordWithMismatchedPasswordsFails() {
    User user = user("max", "max@example.com");
    mockLookup(user);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "max",
                    new ChangePasswordRequestDTO(
                        VALID_NEW_PASSWORD, "Different1!word", "123456", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("do not match");
    verify(userRepository, never()).save(any());
  }

  @Test
  void changePasswordWithMatchingGoogleAccountUpdatesPassword() {
    User user = user("max", "max@example.com");
    mockLookup(user);
    when(googleTokenVerifier.verify("google-token"))
        .thenReturn(
            new GoogleTokenVerifier.GoogleUserInfo("max@example.com", true, "Max", "Mustermann"));

    service.changePassword(
        "max",
        new ChangePasswordRequestDTO(VALID_NEW_PASSWORD, VALID_NEW_PASSWORD, null, "google-token"));

    assertThat(ENCODER.matches(VALID_NEW_PASSWORD, user.getPasswordHash())).isTrue();
    verify(userRepository).save(user);
  }

  @Test
  void deleteAccountWithValidCodeRemovesUserAndUnlinksFriends() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
    User friend = user("erika", "erika@example.com");
    friend.setId(99L);
    friend.getFriends().add(user);
    user.getFriends().add(friend);
    user.setUserTrips(List.of());
    mockLookup(user);
    when(userTripRepository.findByInvitedBy(user)).thenReturn(List.of());

    service.deleteAccount("max", new DeleteAccountRequestDTO("123456", null));

    assertThat(friend.getFriends()).doesNotContain(user);
    verify(userRepository).save(friend);
    verify(userRepository).delete(user);
  }

  @Test
  void deleteAccountDeletesTripsLeftWithoutMembers() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
    Trip trip = new Trip("Solo trip");
    trip.setId(42L);
    user.setUserTrips(List.of(new UserTrip(user, trip, null)));
    mockLookup(user);
    when(userTripRepository.findByInvitedBy(user)).thenReturn(List.of());
    when(userTripRepository.existsByTrip(trip)).thenReturn(false);
    when(eventRepository.findByTrip_Id(42L)).thenReturn(List.of());
    when(ausgabeRepository.findByTrip(trip)).thenReturn(List.of());

    service.deleteAccount("max", new DeleteAccountRequestDTO("123456", null));

    verify(tripRepository).delete(trip);
    verify(userRepository).delete(user);
  }

  @Test
  void deleteAccountKeepsTripsThatStillHaveMembers() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
    Trip trip = new Trip("Shared trip");
    trip.setId(7L);
    user.setUserTrips(List.of(new UserTrip(user, trip, null)));
    mockLookup(user);
    when(userTripRepository.findByInvitedBy(user)).thenReturn(List.of());
    when(userTripRepository.existsByTrip(trip)).thenReturn(true);

    service.deleteAccount("max", new DeleteAccountRequestDTO("123456", null));

    verify(tripRepository, never()).delete(any());
    verify(userRepository).delete(user);
  }

  @Test
  void deleteAccountWithWrongCodeFails() {
    User user = user("max", "max@example.com");
    user.setVerificationCode("123456");
    user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
    mockLookup(user);

    assertThatThrownBy(
            () -> service.deleteAccount("max", new DeleteAccountRequestDTO("000000", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid");
    verify(userRepository, never()).delete(any());
  }

  @Test
  void deleteAccountWithoutVerificationFails() {
    User user = user("max", "max@example.com");
    mockLookup(user);

    assertThatThrownBy(() -> service.deleteAccount("max", new DeleteAccountRequestDTO(null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Verify your email");
    verify(userRepository, never()).delete(any());
  }

  @Test
  void deleteAccountWithMatchingGoogleAccountRemovesUser() {
    User user = user("max", "max@example.com");
    user.setUserTrips(List.of());
    mockLookup(user);
    when(googleTokenVerifier.verify("google-token"))
        .thenReturn(
            new GoogleTokenVerifier.GoogleUserInfo("max@example.com", true, "Max", "Mustermann"));
    when(userTripRepository.findByInvitedBy(user)).thenReturn(List.of());

    service.deleteAccount("max", new DeleteAccountRequestDTO(null, "google-token"));

    verify(userRepository).delete(user);
  }

  @Test
  void changePasswordWithGoogleAccountForDifferentEmailFails() {
    User user = user("max", "max@example.com");
    mockLookup(user);
    when(googleTokenVerifier.verify("google-token"))
        .thenReturn(
            new GoogleTokenVerifier.GoogleUserInfo("someone@else.com", true, "Other", "Person"));

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "max",
                    new ChangePasswordRequestDTO(
                        VALID_NEW_PASSWORD, VALID_NEW_PASSWORD, null, "google-token")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
    verify(userRepository, never()).save(any());
  }
}
