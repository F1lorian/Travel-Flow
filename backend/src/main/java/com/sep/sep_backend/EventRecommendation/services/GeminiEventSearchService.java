package com.sep.sep_backend.EventRecommendation.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.sep_backend.EventRecommendation.dto.EventRecommendationDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
            @Value("${gemini.model:gemini-2.5-flash}") String model
    ) {
        this.restClient = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public List<EventRecommendationDTO> searchEvents(String location) {
        if (location == null || location.isBlank()) {
            return List.of();
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        try {
            String searchText = extractText(callGemini(Map.of(
                    "contents", contents(buildSearchPrompt(location)),
                    "tools", List.of(Map.of("google_search", Map.of())),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "maxOutputTokens", 4096
                    )
            )));

            if (searchText.isBlank()) {
                return List.of();
            }

            String jsonText = extractText(callGemini(Map.of(
                    "contents", contents(buildFormattingPrompt(location, searchText)),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0,
                            "maxOutputTokens", 4096
                    )
            )));

            return parseRecommendations(jsonText);
        } catch (HttpStatusCodeException exception) {
            throw new IllegalStateException("Gemini request failed: " + exception.getResponseBodyAsString(), exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not search events with Gemini.", exception);
        }
    }

    private Map<String, Object> callGemini(Map<String, Object> requestBody) {
        return restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
    }

    private List<Map<String, Object>> contents(String prompt) {
        return List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
        ));
    }


    //here can i change the numer of Result.n 103

    private String buildSearchPrompt(String location) {
        return """
            Search the web for public events in or near "%s" happening in the next 7 days.
            Today's date is %s.

            Find 1 to 5 real upcoming public events for the requested city, region, or country.
            Prefer the most relevant and soonest upcoming events.
            Prefer official event, venue, ticketing, city, tourism, and organizer pages.
            Include each event's name, date/time, price if known, venue or area, short description, and source URL.
            Do not invent details. If a detail is unknown, say it is unknown.
            """.formatted(location, LocalDate.now());
    }


    //here can i change the numer of Result. n 123


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
                """.formatted(location, searchText);
    }

    private List<EventRecommendationDTO> parseRecommendations(String text) {
        String json = stripJsonFences(text);
        try {
            return objectMapper.copy()
                    .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
                    .readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Gemini did not return valid event JSON.", exception);
        }
    }

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
