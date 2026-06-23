package com.sep.sep_backend.event_recommendation.dto;

/** Request body for the event-search endpoint: the city, region, or country to search. */
public record EventSearchRequest(String location) {}
