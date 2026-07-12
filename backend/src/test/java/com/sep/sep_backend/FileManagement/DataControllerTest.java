package com.sep.sep_backend.filemanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class DataControllerTest {

  @Test
  void exportAllEventsReturnsDownloadableIcalResponse() {
    DataService dataService = mock(DataService.class);
    when(dataService.exportAllEventsAsIcal()).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

    ResponseEntity<String> response = new DataController(dataService).exportAllEvents();

    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("text/calendar;charset=UTF-8");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"reisen.ics\"");
    assertThat(response.getBody()).isEqualTo("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
  }
}
