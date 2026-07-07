package com.sep.sep_backend.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.PrivacyPermission;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.PrivacyPermissionRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class PrivacyServiceTest {

  private UserRepository userRepository;
  private PrivacyPermissionRepository privacyPermissionRepository;
  private PrivacyService privacyService;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    privacyPermissionRepository = Mockito.mock(PrivacyPermissionRepository.class);
    privacyService = new PrivacyService(userRepository, privacyPermissionRepository);
  }

  private User createUser(long id, String username, String firstName, String surname) {
    User user = new User();
    user.setId(id);
    user.setName(username);
    user.setFirstName(firstName);
    user.setSurname(surname);
    user.setEmail(username + "@example.com");
    user.setPasswordHash("hash");
    user.setSecurityAnswerHash("answerHash");
    user.setEmailVerified(true);
    return user;
  }

  @Test
  void getPermissionsGibtPermissionsFuerAlleFreundeZurueck() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    PrivacyPermission permission = new PrivacyPermission(owner, friend);
    permission.setCanSeeEvents(true);
    permission.setCanSeeProfile(false);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("cedrick", "cedrick"))
        .thenReturn(Optional.of(owner));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenReturn(Optional.of(permission));

    List<PrivacyPermissionDTO> result = privacyService.getPermissions("cedrick");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).friendId()).isEqualTo(2L);
    assertThat(result.get(0).username()).isEqualTo("noah");
    assertThat(result.get(0).firstName()).isEqualTo("Noah");
    assertThat(result.get(0).surname()).isEqualTo("Bucic");
    assertThat(result.get(0).canSeeEvents()).isTrue();
    assertThat(result.get(0).canSeeProfile()).isFalse();
  }

  @Test
  void getPermissionsNutztDefaultFalseWennKeinePermissionExistiert() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("cedrick", "cedrick"))
        .thenReturn(Optional.of(owner));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenReturn(Optional.empty());

    List<PrivacyPermissionDTO> result = privacyService.getPermissions("cedrick");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).canSeeEvents()).isFalse();
    assertThat(result.get(0).canSeeProfile()).isFalse();
  }

  @Test
  void updatePermissionSpeichertPermissionFuerFreund() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    UpdatePrivacyPermissionRequestDTO request = new UpdatePrivacyPermissionRequestDTO(true, false);

    AtomicReference<PrivacyPermission> savedPermission = new AtomicReference<>();

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("cedrick", "cedrick"))
        .thenReturn(Optional.of(owner));
    when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenAnswer(invocation -> Optional.ofNullable(savedPermission.get()));
    when(privacyPermissionRepository.save(any(PrivacyPermission.class)))
        .thenAnswer(
            invocation -> {
              PrivacyPermission permission = invocation.getArgument(0);
              savedPermission.set(permission);
              return permission;
            });

    PrivacyPermissionDTO result = privacyService.updatePermission("cedrick", 2L, request);

    assertThat(result.friendId()).isEqualTo(2L);
    assertThat(result.canSeeEvents()).isTrue();
    assertThat(result.canSeeProfile()).isFalse();

    verify(privacyPermissionRepository).save(any(PrivacyPermission.class));
  }

  @Test
  void updatePermissionWirftForbiddenWennUserKeinFreundIst() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User notFriend = createUser(2L, "noah", "Noah", "Bucic");

    UpdatePrivacyPermissionRequestDTO request = new UpdatePrivacyPermissionRequestDTO(true, true);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("cedrick", "cedrick"))
        .thenReturn(Optional.of(owner));
    when(userRepository.findById(2L)).thenReturn(Optional.of(notFriend));

    assertThatThrownBy(() -> privacyService.updatePermission("cedrick", 2L, request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Permissions can only be changed for friends");

    verify(privacyPermissionRepository, never()).save(any());
  }

  @Test
  void getSharedProfileGibtProfilZurueckWennPermissionErlaubtIst() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    PrivacyPermission permission = new PrivacyPermission(owner, friend);
    permission.setCanSeeProfile(true);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("noah", "noah"))
        .thenReturn(Optional.of(friend));
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenReturn(Optional.of(permission));

    SharedUserProfileDTO result = privacyService.getSharedProfile("noah", 1L);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.username()).isEqualTo("cedrick");
    assertThat(result.firstName()).isEqualTo("Cedrick");
    assertThat(result.surname()).isEqualTo("Dibengue");
  }

  @Test
  void getSharedProfileWirftForbiddenOhneProfilPermission() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    PrivacyPermission permission = new PrivacyPermission(owner, friend);
    permission.setCanSeeProfile(false);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("noah", "noah"))
        .thenReturn(Optional.of(friend));
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenReturn(Optional.of(permission));

    assertThatThrownBy(() -> privacyService.getSharedProfile("noah", 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("You are not allowed to see this profile");
  }

  @Test
  void getSharedEventsGibtEventsZurueckWennPermissionErlaubtIst() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    Event event = new Event();
    event.setId(10L);
    event.setName("Museum visit");
    event.setStartTime(LocalDateTime.of(2026, 6, 1, 10, 0));
    event.setEndTime(LocalDateTime.of(2026, 6, 1, 12, 0));
    event.setKosten(25);

    owner.setEvents(List.of(event));

    PrivacyPermission permission = new PrivacyPermission(owner, friend);
    permission.setCanSeeEvents(true);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("noah", "noah"))
        .thenReturn(Optional.of(friend));
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenReturn(Optional.of(permission));

    List<SharedEventDTO> result = privacyService.getSharedEvents("noah", 1L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(10L);
    assertThat(result.get(0).name()).isEqualTo("Museum visit");
    assertThat(result.get(0).kosten()).isEqualTo(25);
  }

  @Test
  void getSharedEventsWirftForbiddenOhneEventPermission() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User friend = createUser(2L, "noah", "Noah", "Bucic");
    owner.getFriends().add(friend);

    PrivacyPermission permission = new PrivacyPermission(owner, friend);
    permission.setCanSeeEvents(false);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("noah", "noah"))
        .thenReturn(Optional.of(friend));
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(privacyPermissionRepository.findByOwnerAndFriend(owner, friend))
        .thenReturn(Optional.of(permission));

    assertThatThrownBy(() -> privacyService.getSharedEvents("noah", 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("You are not allowed to see these events");
  }

  @Test
  void getSharedProfileWirftForbiddenWennUsersKeineFreundeSind() {
    User owner = createUser(1L, "cedrick", "Cedrick", "Dibengue");
    User stranger = createUser(2L, "noah", "Noah", "Bucic");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("noah", "noah"))
        .thenReturn(Optional.of(stranger));
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

    assertThatThrownBy(() -> privacyService.getSharedProfile("noah", 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Only friends can access this information");
  }
}
