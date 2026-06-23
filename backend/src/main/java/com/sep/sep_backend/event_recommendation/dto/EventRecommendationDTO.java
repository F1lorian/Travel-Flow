package com.sep.sep_backend.event_recommendation.dto;

/**
 * One recommended event returned to the frontend. Matches the JSON shape the formatting prompt asks
 * Gemini to produce.
 *
 * @param dateTime ISO local date-time, e.g. {@code 2026-06-01T18:30:00}
 * @param price EUR amount; {@code 0} = free, {@code null} = unknown
 * @param sourceUrl booking/info page, or empty string if unknown
 */
public record EventRecommendationDTO(
    String name,
    String dateTime,
    Double price,
    String description,
    String location,
    String sourceUrl) {}
