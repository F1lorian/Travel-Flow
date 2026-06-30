package com.sep.sep_backend.eventRecommendation.services;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.sep_backend.eventRecommendation.dto.EventRecommendationDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Finds real upcoming public events for a location using Google's Gemini API.
 *
 * <p>The search runs as two separate Gemini calls so each step has one job:
 *
 * <ol>
 *   <li><b>Search</b> — Gemini with the {@code google_search} tool grounds the answer in real web
 *       results and returns free-text notes about events.
 *   <li><b>Format</b> — a second call turns those notes into a strict JSON array that maps cleanly
 *       onto {@link EventRecommendationDTO}.
 * </ol>
 *
 * Splitting the steps keeps the JSON reliable: the model only formats text it already has, instead
 * of searching and formatting in one go.
 */
@Service
public class GeminiEventSearchService {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;

  public GeminiEventSearchService(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${gemini.api-key:}") String apiKey,
      @Value("${gemini.model:gemini-2.5-flash}") String model) {
    this.restClient =
        restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.model = model;
  }

  /**
   * Returns 1–5 recommended events for the given location, or an empty list if the location is
   * blank or the model finds nothing. Any API/parse failure is rethrown as an {@link
   * IllegalStateException}.
   */
  public List<EventRecommendationDTO> searchEvents(String location) {
    // Nothing to search for — skip the API call entirely.
    if (location == null || location.isBlank()) {
      return List.of();
    }

    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("Gemini API key is not configured.");
    }

    try {
      // Step 1 — web-grounded search. The google_search tool lets Gemini look up
      // real events; low temperature (0.2) keeps it factual rather than creative.
      String searchText =
          extractText(
              callGemini(
                  Map.of(
                      "contents", contents(buildSearchPrompt(location)),
                      "tools", List.of(Map.of("google_search", Map.of())),
                      "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 4096))));

      // Model found no events to describe — return empty instead of formatting "".
      if (searchText.isBlank()) {
        return List.of();
      }

      // Step 2 — formatting only. responseMimeType=application/json + temperature 0
      // force a deterministic, pure-JSON answer we can deserialize directly.
      String jsonText =
          extractText(
              callGemini(
                  Map.of(
                      "contents", contents(buildFormattingPrompt(location, searchText)),
                      "generationConfig",
                          Map.of(
                              "responseMimeType", "application/json",
                              "temperature", 0,
                              "maxOutputTokens", 4096))));

      return parseRecommendations(jsonText);
    } catch (HttpStatusCodeException exception) {
      // Gemini answered with an error status — surface its body for debugging.
      throw new IllegalStateException(
          "Gemini request failed: " + exception.getResponseBodyAsString(), exception);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Could not search events with Gemini.", exception);
    }
  }

  // Sends one generateContent request and returns the raw response as a Map.
  private Map<String, Object> callGemini(Map<String, Object> requestBody) {
    return restClient
        .post()
        .uri("/v1beta/models/{model}:generateContent", model)
        .header("x-goog-api-key", apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(Map.class);
  }

  private List<Map<String, Object>> contents(String prompt) {
    return List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));
  }

  // The number of events (1 to 5) is set in this prompt text — change it here.
  private String buildSearchPrompt(String location) {
    return """
            Search the web for public events in or near "%s" happening in the next 7 days.
            Today's date is %s.

            Find 1 to 5 real upcoming public events for the requested city, region, or country.
            Prefer the most relevant and soonest upcoming events.
            Prefer official event, venue, ticketing, city, tourism, and organizer pages.
            Include each event's name, date/time, price if known, venue or area, short description, and source URL.
            Do not invent details. If a detail is unknown, say it is unknown.
            """
        .formatted(location, LocalDate.now());
  }

  // The result count (1 to 5) and the exact JSON shape are defined in this prompt.
  private String buildFormattingPrompt(String location, String searchText) {
    return """
                Convert the event search notes below into JSON for "%s".

                Return only a JSON array. Do not include markdown or explanation.



                Return 1 to 5 event objects from the notes that happen in the next 7 days. Prefer the most relevant and soonest upcoming events.

                Each object must have exactly these fields:
                - name: string
                - dateTime: string in ISO local format like 2026-06-01T18:30:00. If exact time is not listed, use 18:00:00.
                - price: number in EUR. If free, use 0. If unknown, use null.
                - description: string, one or two short sentences.
                - location: string with venue or area.
                - sourceUrl: string with booking or info page.

                Do not invent details. If price is unknown, use null. If sourceUrl is unknown, use an empty string.
                Escape line breaks inside string values as \\n. Do not put literal line breaks inside JSON strings.

                Event search notes:
                %s
                """
        .formatted(location, searchText);
  }

  // Deserializes the JSON array into DTOs. ALLOW_UNQUOTED_CONTROL_CHARS tolerates
  // stray newlines the model sometimes leaves inside string values.
  private List<EventRecommendationDTO> parseRecommendations(String text) {
    String json = stripJsonFences(text);
    try {
      return objectMapper
          .copy()
          .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
          .readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Gemini did not return valid event JSON.", exception);
    }
  }

  // Pulls the plain text out of Gemini's nested response shape
  // (candidates[0].content.parts[*].text), joining all text parts.
  private String extractText(Map<String, Object> response) {
    List<Map<String, Object>> candidates = listOfMaps(response.get("candidates"));
    if (candidates.isEmpty()) {
      return "[]";
    }

    Map<String, Object> content = map(candidates.getFirst().get("content"));
    List<Map<String, Object>> parts = listOfMaps(content.get("parts"));
    StringBuilder text = new StringBuilder();

    for (Map<String, Object> part : parts) {
      Object partText = part.get("text");
      if (partText instanceof String value) {
        text.append(value);
      }
    }

    return text.toString().trim();
  }

  // Extracts the JSON array from the model's reply. Preferred path: slice from the
  // first '[' to the last ']'. Fallback: strip ```json / ``` code fences if present.
  private String stripJsonFences(String text) {
    String trimmed = text == null ? "" : text.trim();

    int arrayStart = trimmed.indexOf('[');
    int arrayEnd = trimmed.lastIndexOf(']');
    if (arrayStart >= 0 && arrayEnd > arrayStart) {
      return trimmed.substring(arrayStart, arrayEnd + 1);
    }

    if (trimmed.startsWith("```json")) {
      trimmed = trimmed.substring("```json".length());
    } else if (trimmed.startsWith("```")) {
      trimmed = trimmed.substring("```".length());
    }

    int fenceStart = trimmed.indexOf("```");
    if (fenceStart >= 0) {
      trimmed = trimmed.substring(0, fenceStart);
    }

    return trimmed.trim();
  }

  // The next two helpers safely narrow the loosely-typed JSON (Object) values
  // into the Map/List shapes we expect, returning empty rather than throwing.
  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    if (value instanceof Map<?, ?> rawMap) {
      return (Map<String, Object>) rawMap;
    }
    return Map.of();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOfMaps(Object value) {
    if (value instanceof List<?> rawList) {
      return rawList.stream()
          .filter(Map.class::isInstance)
          .map(item -> (Map<String, Object>) item)
          .toList();
    }
    return List.of();
  }
}
