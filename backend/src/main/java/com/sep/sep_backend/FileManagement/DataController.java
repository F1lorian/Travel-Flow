package com.sep.sep_backend.FileManagement;

import com.sep.sep_backend.accountmanagement.CurrentUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
  private final CurrentUserService currentUserService;

  /**
   * Creates a data controller that delegates export requests to the data service.
   *
   * @param dataService service used to create exported data
   * @param currentUserService service used to resolve the authenticated user
   */
  public DataController(DataService dataService, CurrentUserService currentUserService) {
    this.dataService = dataService;
    this.currentUserService = currentUserService;
  }

  /**
   * Exports the logged-in user's events and trips as an iCalendar file.
   *
   * @return response containing the iCalendar content and download headers
   */
  @GetMapping(value = "/ical", produces = "text/calendar")
  public ResponseEntity<String> exportAllEvents(Authentication authentication) {
    Long userId = currentUserService.requireUser(authentication).getId();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reisen.ics\"")
        .body(dataService.exportAllEventsAsIcal(userId));
  }
}
