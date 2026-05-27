package com.sep.sep_backend.EventRecommendation.controllers;

import com.sep.sep_backend.EventRecommendation.dto.EventRecommendationDTO;
import com.sep.sep_backend.EventRecommendation.dto.EventSearchRequest;
import com.sep.sep_backend.EventRecommendation.services.GeminiEventSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class EventRecommendationController {

    private final GeminiEventSearchService geminiEventSearchService;

    public EventRecommendationController(GeminiEventSearchService geminiEventSearchService) {
        this.geminiEventSearchService = geminiEventSearchService;
    }

    @PostMapping("/event-search")
    public ResponseEntity<List<EventRecommendationDTO>> searchEvents(@RequestBody EventSearchRequest request) {
        return ResponseEntity.ok(geminiEventSearchService.searchEvents(request.location()));
    }
}
