package com.sep.sep_backend.EventRecommendation.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep.sep_backend.EventRecommendation.dto.EventRecommendationDTO;
import com.sep.sep_backend.EventRecommendation.services.GeminiEventSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @WebMvcTest loads only the web layer for this controller; the Gemini service is
// mocked, so the test checks request/response wiring without calling the real API.
@WebMvcTest(EventRecommendationController.class)
class EventRecommendationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GeminiEventSearchService geminiEventSearchService;

  @Test
  void searchEvents_returnsRecommendations() throws Exception {
    // Arrange: stub the service to return one known recommendation for "Dortmund".
    when(geminiEventSearchService.searchEvents("Dortmund"))
        .thenReturn(
            List.of(
                new EventRecommendationDTO(
                    "Food Festival",
                    "2026-06-01T18:00:00",
                    15.0,
                    "Food and music event.",
                    "Dortmund",
                    "https://example.com")));

    // Act + Assert: POST the location and verify the JSON response maps the DTO correctly.
    mockMvc
        .perform(
            post("/api/trips/event-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\": \"Dortmund\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Food Festival"))
        .andExpect(jsonPath("$[0].price").value(15.0));
  }
}
