package com.sep.sep_backend.FileManagement;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "http://localhost:4200")
class DataController {
  private final DataService dataService;

  public DataController(DataService dataService) {
    this.dataService = dataService;
  }

  //Gibt Dass ics file zurück
  @GetMapping(value = "/ical", produces = "text/calendar")
  public ResponseEntity<String> exportAllEvents() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reisen.ics\"")
        .body(dataService.exportAllEventsAsIcal());
  }
}
