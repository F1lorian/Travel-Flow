package com.sep.sep_backend.EventRecommendation.dto;

public record EventRecommendationDTO(
    String name,
    String dateTime,
    Double price,
    String description,
    String location,
    String sourceUrl) {}
