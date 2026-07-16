package com.sep.sep_backend.FileManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.accountmanagement.CurrentUserService;
import com.sep.sep_backend.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class DataControllerTest {

  @Test
  void exportAllEventsReturnsDownloadableIcalResponse() {
    com.sep.sep_backend.FileManagement.DataService dataService =
        mock(com.sep.sep_backend.FileManagement.DataService.class);
    CurrentUserService currentUserService = mock(CurrentUserService.class);
    Authentication authentication = mock(Authentication.class);
    User user = new User();
    user.setId(7L);
    when(currentUserService.requireUser(authentication)).thenReturn(user);
    when(dataService.exportAllEventsAsIcal(7L)).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

    ResponseEntity<String> response =
        new com.sep.sep_backend.FileManagement.DataController(dataService, currentUserService)
            .exportAllEvents(authentication);

    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("text/calendar;charset=UTF-8");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"reisen.ics\"");
    assertThat(response.getBody()).isEqualTo("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
  }
}
