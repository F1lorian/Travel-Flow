package com.sep.sep_backend.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class PrivacyControllerTest {

    private final PrivacyService privacyService = mock(PrivacyService.class);
    private final PrivacyController privacyController = new PrivacyController(privacyService);

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken("cedrick", null, List.of());
    }

    @Test
    void getPermissionsGibtPrivacySettingsZurueck() {
        List<PrivacyPermissionDTO> permissions =
                List.of(new PrivacyPermissionDTO(2L, "noah", "Noah", "Bucic", true, false));

        when(privacyService.getPermissions("cedrick")).thenReturn(permissions);

        List<PrivacyPermissionDTO> result = privacyController.getPermissions(authentication());

        assertThat(result).isSameAs(permissions);
        verify(privacyService).getPermissions("cedrick");
    }

    @Test
    void updatePermissionGibtAktualisiertePermissionZurueck() {
        UpdatePrivacyPermissionRequestDTO request =
                new UpdatePrivacyPermissionRequestDTO(true, false);
        PrivacyPermissionDTO permission =
                new PrivacyPermissionDTO(2L, "noah", "Noah", "Bucic", true, false);

        when(privacyService.updatePermission("cedrick", 2L, request)).thenReturn(permission);

        PrivacyPermissionDTO result =
                privacyController.updatePermission(2L, request, authentication());

        assertThat(result).isSameAs(permission);
        verify(privacyService).updatePermission("cedrick", 2L, request);
    }

    @Test
    void getSharedProfileGibtProfilZurueck() {
        SharedUserProfileDTO profile =
                new SharedUserProfileDTO(2L, "noah", "Noah", "Bucic");

        when(privacyService.getSharedProfile("cedrick", 2L)).thenReturn(profile);

        SharedUserProfileDTO result = privacyController.getSharedProfile(2L, authentication());

        assertThat(result).isSameAs(profile);
        verify(privacyService).getSharedProfile("cedrick", 2L);
    }

    @Test
    void getSharedEventsGibtEventsZurueck() {
        List<SharedEventDTO> events =
                List.of(
                        new SharedEventDTO(
                                1L,
                                "Museum visit",
                                LocalDateTime.of(2026, 6, 1, 10, 0),
                                LocalDateTime.of(2026, 6, 1, 12, 0),
                                25,
                                null));

        when(privacyService.getSharedEvents("cedrick", 2L)).thenReturn(events);

        List<SharedEventDTO> result = privacyController.getSharedEvents(2L, authentication());

        assertThat(result).isSameAs(events);
        verify(privacyService).getSharedEvents("cedrick", 2L);
    }

    @Test
    void getPermissionsWirftUnauthorizedWennAuthenticationFehlt() {
        assertThatThrownBy(() -> privacyController.getPermissions(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User is not authenticated");
    }
}