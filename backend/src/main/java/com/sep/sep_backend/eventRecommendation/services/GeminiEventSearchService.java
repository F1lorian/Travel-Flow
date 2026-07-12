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
 * Suggests plausible upcoming public events for a location using Google's Gemini API.
 *
 * <p>This asks Gemini directly for a JSON array of events, without the {@code google_search}
 * grounding tool: that tool has zero free quota on non-billing Google Cloud projects, which made
 * every search fail with a 429 regardless of which model was configured. Without web grounding,
 * Gemini answers from its training knowledge instead of live listings, so results are best-effort
 * suggestions (e.g. well-known recurring festivals/markets) rather than confirmed current events.
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
      // Single call, no google_search tool: responseMimeType=application/json forces a
      // pure-JSON answer, and a moderate temperature lets the model draw on plausible,
      // well-known recurring events instead of just refusing to answer.
      String jsonText =
          extractText(
              callGemini(
                  Map.of(
                      "contents", contents(buildPrompt(location)),
                      "generationConfig",
                          Map.of(
                              "responseMimeType",
                              "application/json",
                              "temperature",
                              0.3,
                              "maxOutputTokens",
                              4096))));

      if (jsonText.isBlank()) {
        return List.of();
      }

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

  // The number of events (1 to 5) and the exact JSON shape are defined in this prompt.
  // No web access here, so the model draws on general/training knowledge instead of live listings.
  private String buildPrompt(String location) {
    return """
            Suggest 1 to 5 public events that plausibly take place in or near "%s" in the next 7 days,
            based on your general knowledge of recurring festivals, markets, concerts, and other
            public events typical for this location and time of year. Today's date is %s.

            You do not have web access, so these cannot be confirmed live listings. Prefer
            well-known, likely-recurring events over implausible one-off guesses. If you cannot
            think of any reasonable suggestion for this location, return an empty JSON array.

            Return only a JSON array. Do not include markdown or explanation.

            Each object must have exactly these fields:
            - name: string
            - dateTime: string in ISO local format like 2026-06-01T18:30:00. If exact time is unknown, use 18:00:00.
            - price: number in EUR. If free, use 0. If unknown, use null.
            - description: string, one or two short sentences. Make clear this is a general suggestion, not a confirmed listing.
            - location: string with venue or area.
            - sourceUrl: string with a general info page if known, otherwise an empty string.

            If price is unknown, use null. If sourceUrl is unknown, use an empty string.
            Escape line breaks inside string values as \\n. Do not put literal line breaks inside JSON strings.
            """
        .formatted(location, LocalDate.now());
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
