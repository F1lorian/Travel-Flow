package com.sep.sep_backend.gmailimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.ThinkingConfig;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link AiEventExtractor} backed by Google Gemini. Prompts the model for a strict JSON object and
 * maps it into an {@link ExtractedEventDTO}. Any error (missing key, network issue, malformed
 * response) results in an empty Optional so the parser can fall back to heuristics.
 */
@Component
public class GeminiEventExtractor implements AiEventExtractor {

  private final ObjectMapper objectMapper;
  private final String model;

  /**
   * A single, reused Gemini client (thread-safe). Built once so a scan that extracts many emails
   * concurrently does not pay client-construction cost per email. {@code null} when no API key is
   * configured, in which case the extractor reports itself unavailable.
   */
  private final Client client;

  public GeminiEventExtractor(
      ObjectMapper objectMapper,
      @Value("${gemini.api-key:}") String apiKey,
      @Value("${gemini.model:gemini-2.5-flash}") String model,
      @Value("${gemini.request-timeout-ms:15000}") int requestTimeoutMs) {
    this.objectMapper = objectMapper;
    this.model = model;
    // Cap each request so one slow model call cannot stall the whole scan past the proxy timeout.
    this.client =
        StringUtils.hasText(apiKey)
            ? Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(requestTimeoutMs).build())
                .build()
            : null;
  }

  @Override
  public boolean isAvailable() {
    return client != null;
  }

  @Override
  public Optional<ExtractedEventDTO> extract(String subject, String body) {
    if (client == null || !StringUtils.hasText(body)) {
      return Optional.empty();
    }
    try {
      GenerateContentConfig config =
          GenerateContentConfig.builder()
              .responseMimeType("application/json")
              // A low temperature keeps the model close to the email text and reduces
              // hallucination.
              .temperature(0.1f)
              // Disable the model's "thinking" phase: this is a simple structured-extraction task,
              // and thinking adds several seconds of latency per call, which a scan multiplies
              // across every email.
              .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
              .build();
      GenerateContentResponse response =
          client.models.generateContent(model, buildPrompt(subject, body), config);
      return Optional.of(mapResponse(subject, response.text()));
    } catch (Exception exception) {
      // Never propagate AI failures: the parser falls back to heuristic extraction.
      return Optional.empty();
    }
  }

  @PreDestroy
  void shutdown() {
    if (client != null) {
      client.close();
    }
  }

  private ExtractedEventDTO mapResponse(String subject, String json) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    ExtractedEventDTO dto = new ExtractedEventDTO();
    dto.setTitle(textOrNull(node, "title", subject));
    dto.setDescription(textOrNull(node, "description", null));
    dto.setDate(parseDate(node.path("date").asText(null)));
    dto.setStartTime(parseTime(node.path("startTime").asText(null)));
    dto.setEndTime(parseTime(node.path("endTime").asText(null)));
    dto.setTimeZone(textOrNull(node, "timeZone", null));
    dto.setLocation(textOrNull(node, "location", null));
    dto.setOrganizer(textOrNull(node, "organizer", null));
    return dto;
  }

  private String buildPrompt(String subject, String body) {
    return """
        Extract calendar event information from this email.
        Respond ONLY with a JSON object using exactly these keys:
        {
          "title": "",
          "description": "",
          "date": "yyyy-MM-dd",
          "startTime": "HH:mm",
          "endTime": "HH:mm",
          "timeZone": "",
          "location": "",
          "organizer": ""
        }

        Rules:
        - Use an empty string for any field that is not present in the email.
        - Never invent values; only extract what the email actually states.
        - date must be yyyy-MM-dd; startTime and endTime must be 24-hour HH:mm.
        - timeZone should be an IANA name (e.g. Europe/Berlin) if the email states one.
        - organizer is the person or company hosting the event, if named.

        Subject:
        %s

        Body:
        %s
        """
        .formatted(subject == null ? "" : subject, body);
  }

  private String textOrNull(JsonNode node, String field, String fallback) {
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception exception) {
      return null;
    }
  }

  private LocalTime parseTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalTime.parse(value.trim());
    } catch (Exception exception) {
      return null;
    }
  }
}
