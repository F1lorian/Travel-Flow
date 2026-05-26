package com.sep.sep_backend.TripManagement.dto;

public record EventRecommendationDTO(
        String name,
        String dateTime,
        Double price,
        String description,
        String location,
        String sourceUrl
) {
}
