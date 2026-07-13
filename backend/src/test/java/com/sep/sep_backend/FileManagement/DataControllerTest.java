package com.sep.sep_backend.FileManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class DataControllerTest {

  @Test
  void exportAllEventsReturnsDownloadableIcalResponse() {
    com.sep.sep_backend.FileManagement.DataService dataService = mock(com.sep.sep_backend.FileManagement.DataService.class);
    when(dataService.exportAllEventsAsIcal()).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

    ResponseEntity<String> response = new com.sep.sep_backend.FileManagement.DataController(dataService).exportAllEvents();

    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("text/calendar;charset=UTF-8");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"reisen.ics\"");
    assertThat(response.getBody()).isEqualTo("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
  }
}
