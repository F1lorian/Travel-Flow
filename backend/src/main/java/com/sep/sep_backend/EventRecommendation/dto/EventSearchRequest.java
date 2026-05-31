package com.sep.sep_backend.EventRecommendation.dto;

/** Request body for the event-search endpoint: the city, region, or country to search. */
public record EventSearchRequest(String location) {}
