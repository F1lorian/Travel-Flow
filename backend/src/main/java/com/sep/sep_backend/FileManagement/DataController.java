package com.sep.sep_backend.FileManagement;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for exporting application data in calendar formats. */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "http://localhost:4200")
class DataController {
  private final DataService dataService;

  /**
   * Creates a data controller that delegates export requests to the data service.
   *
   * @param dataService service used to create exported data
   */
  public DataController(DataService dataService) {
    this.dataService = dataService;
  }

  /**
   * Exports all events and trips as an iCalendar file.
   *
   * @return response containing the iCalendar content and download headers
   */
  @GetMapping(value = "/ical", produces = "text/calendar")
  public ResponseEntity<String> exportAllEvents() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reisen.ics\"")
        .body(dataService.exportAllEventsAsIcal());
  }
}
