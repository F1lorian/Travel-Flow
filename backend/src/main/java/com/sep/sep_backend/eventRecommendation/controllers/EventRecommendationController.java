package com.sep.sep_backend.eventRecommendation.controllers;

import com.sep.sep_backend.eventRecommendation.dto.EventRecommendationDTO;
import com.sep.sep_backend.eventRecommendation.dto.EventSearchRequest;
import com.sep.sep_backend.eventRecommendation.services.GeminiEventSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for AI event recommendations. Delegates the actual work to {@link
 * GeminiEventSearchService} and just maps the result to an HTTP response.
 */
@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class EventRecommendationController {

  private final GeminiEventSearchService geminiEventSearchService;

  public EventRecommendationController(GeminiEventSearchService geminiEventSearchService) {
    this.geminiEventSearchService = geminiEventSearchService;
  }

  // POST /api/trips/event-search — body is a location, returns 1–5 suggested events.
  @PostMapping("/event-search")
  public ResponseEntity<List<EventRecommendationDTO>> searchEvents(
      @RequestBody EventSearchRequest request) {
    return ResponseEntity.ok(geminiEventSearchService.searchEvents(request.location()));
  }
}
